package dev.fragruns.core;

import java.util.*;
import java.util.function.ToDoubleFunction;

/** An anonymous overflow is a possible reward. Only itemized server evidence books a fragment. */
public final class FragmentStash {
    public record Mark(String scope,boolean possible,int reserved) {
        public Mark { if (scope==null || scope.isBlank() || reserved<0) throw new IllegalArgumentException("Invalid stash notice"); }
    }
    public record Shared(Giant giant,int count,List<UUID> runs) {
        public Shared {
            runs=List.copyOf(runs);
            if (giant==null || count<1 || count>runs.size()) throw new IllegalArgumentException("Invalid shared stash receipt");
        }
    }
    public record Data(Map<UUID,Mark> marks,List<Shared> shared) {}
    private final Map<UUID,Mark> marks=new LinkedHashMap<>();
    private final List<Shared> shared=new ArrayList<>();
    public void clear() { marks.clear(); shared.clear(); }
    public Data snapshot() { return new Data(Map.copyOf(marks),List.copyOf(shared)); }
    static void validate(Data data,Set<UUID> known) {
        if (data==null) return;
        if (data.marks==null || data.shared==null || !known.containsAll(data.marks.keySet())
                || data.marks.values().stream().anyMatch(Objects::isNull) || data.shared.stream().anyMatch(s->s==null || !known.containsAll(s.runs))) throw new IllegalArgumentException("Invalid saved stash references");
    }
    void restore(Data data) {
        clear();
        if (data!=null) { marks.putAll(data.marks); shared.addAll(data.shared); }
    }
    public void overflow(UUID run,String scope) {
        if (run!=null && scope!=null) marks.putIfAbsent(run,new Mark(scope,true,0));
    }
    public void deposited(UUID run,String scope,int amount) {
        if (run==null || scope==null || amount<=0) return;
        Mark prior=marks.get(run);
        marks.put(run,new Mark(scope,false,Math.max(amount,prior==null?0:prior.reserved)));
    }
    public boolean outstanding() { return !marks.isEmpty(); }
    public boolean awaiting(UUID run) { Mark m=marks.get(run);return m!=null && m.possible; }
    public void physicalInventory(UUID run) {
        Mark mark=marks.get(run);
        if (mark!=null && mark.reserved==0 && mark.possible) marks.remove(run);
    }
    public boolean shared(UUID run) { return shared.stream().anyMatch(s->s.runs.contains(run)); }
    public int sharedCount(Giant giant,List<Session.Run> finished) {
        if (shared.isEmpty()) return 0;
        Set<UUID> ids=new HashSet<>();
        for (var r:finished) if (r.details()!=null) ids.add(r.details().id());
        int count=0;
        for (var batch:shared) if (batch.giant==giant) {
            int partial=(int)batch.runs.stream().filter(id->!ids.contains(id)).count();
            count+=Math.max(0,batch.count-partial);
        }
        return count;
    }
    private record Candidate(Session session,UUID run) {}
    public record Target(String session,UUID run) {}
    public record Choice(UUID id,Giant giant,List<Target> targets,Target selected) {
        public Choice {
            targets=List.copyOf(targets);
            if (id==null || giant==null || targets.isEmpty() || selected!=null && !targets.contains(selected))
                throw new IllegalArgumentException("Invalid stash receipt");
        }
        public boolean accepts(Target target) { return selected==null?targets.contains(target):selected.equals(target); }
    }
    public record Result(Set<Session> changed,List<Choice> choices) {}
    public static Result settle(List<Session> sessions,String scope,Map<Giant,Integer> received,boolean emptied,boolean fromBalance,
                                List<Choice> previousChoices,ToDoubleFunction<Giant> price) {
        Set<Session> changed=new LinkedHashSet<>();
        List<Choice> choices=new ArrayList<>();
        for (var entry:received.entrySet()) {
            Giant giant=entry.getKey(); int left=entry.getValue();
            if (left<=0) continue;
            List<Candidate> candidates=new ArrayList<>();
            for (var session:sessions) {
                var book=session.stash();
                for (var m:List.copyOf(book.marks.entrySet())) {
                    UUID id=m.getKey(); Mark mark=m.getValue();
                    if (!scope.equals(mark.scope)) continue;
                    var run=session.stashRun(id);
                    if (run==null || run.giant()!=giant) continue;
                    int used=fromBalance?0:Math.min(mark.reserved,left);
                    if (used>0) {
                        left-=used; changed.add(session);
                        if (mark.reserved==used && !mark.possible) book.marks.remove(id);
                        else book.marks.put(id,new Mark(scope,mark.possible,mark.reserved-used));
                    }
                    if (mark.possible && run.details().giantDefeated() && run.fragments()==0) candidates.add(new Candidate(session,id));
                    else if (!fromBalance && mark.possible && run.fragments()>0 && left>0 && mark.reserved==0) {
                        // A pickup packet can accompany an overflow without ever changing the inventory.
                        left-=Math.min(left,run.fragments());book.marks.remove(id);changed.add(session);
                    }
                }
            }
            if (left==0 || candidates.isEmpty()) continue;
            Map<Session,List<UUID>> owners=new LinkedHashMap<>();
            for (var candidate:candidates) owners.computeIfAbsent(candidate.session,k->new ArrayList<>()).add(candidate.run);
            int available=owners.entrySet().stream().mapToInt(o->o.getValue().size()-o.getKey().stash().previous(giant,o.getValue())).sum();
            var targets=candidates.stream().map(c->new Target(c.session.id(),c.run)).toList();
            int earlier=(int)previousChoices.stream().filter(c->c.giant==giant && targets.containsAll(c.targets)).count();
            if (earlier>0 || owners.size()>1 && left<available) {
                int count=Math.min(left,Math.max(0,available-earlier));
                for (int i=0;i<count;i++) choices.add(new Choice(UUID.randomUUID(),giant,targets,null));
                if (fromBalance && count>0) {
                    var first=candidates.getFirst();var book=first.session.stash();
                    book.marks.put(first.run,new Mark(scope,true,book.marks.get(first.run).reserved+count));changed.add(first.session);
                }
                continue;
            }
            for (var owner:owners.entrySet()) {
                Session s=owner.getKey(); var ids=owner.getValue();var book=s.stash();
                int prior=book.previous(giant,ids);
                int amount=Math.min(left,ids.size()-prior);
                if (amount<=0) continue;
                left-=amount; changed.add(s);
                if (amount+prior==ids.size()) {
                    int held=ids.stream().map(book.marks::get).mapToInt(Mark::reserved).sum();
                    int fresh=amount;
                    // Give any active run a newly booked unit. Previously shared value is already in the session ledger.
                    ids.sort(Comparator.comparing(id->!id.equals(s.currentRunId())));
                    for (UUID id:ids) {
                        if (fresh>0) { s.creditStash(id,giant,1,price.applyAsDouble(giant));fresh--; }
                        else s.assignArchivedFragment(id,giant,1,price.applyAsDouble(giant));
                        book.marks.remove(id);
                    }
                    book.shared.removeIf(batch->batch.giant==giant && ids.containsAll(batch.runs));
                    int reserved=held+(fromBalance?amount:0);
                    if (reserved>0) book.marks.put(ids.getFirst(),new Mark(scope,false,reserved));
                } else {
                    // The server identified the quantity but did not say which of these encounters dropped it.
                    s.creditStash(null,giant,amount,price.applyAsDouble(giant));
                    book.shared.add(new Shared(giant,amount,ids));
                    if (fromBalance) book.marks.put(ids.getFirst(),new Mark(scope,true,book.marks.get(ids.getFirst()).reserved+amount));
                }
            }
        }
        if (emptied) for (Session s:sessions) {
            if (s.stash().marks.values().removeIf(m->scope.equals(m.scope))) changed.add(s);
        }
        return new Result(changed,List.copyOf(choices));
    }
    public boolean hasEvidence(Session s,String scope,Giant giant) {
        for (var entry:marks.entrySet()) if (entry.getValue().scope.equals(scope)) {
            var run=s.stashRun(entry.getKey());
            if (run!=null && (run.giant()==null || run.giant()==giant)) return true;
        }
        return false;
    }
    public static boolean applyChoice(Session session,Choice choice,Target target,ToDoubleFunction<Giant> price) {
        if (!target.equals(choice.selected) || !target.session.equals(session.id())) return false;
        var r=session.stashRun(target.run);
        if (r==null || r.giant()!=choice.giant || !r.details().giantDefeated()) return false;
        if (r.fragments()==0) session.creditStash(target.run,choice.giant,1,price.applyAsDouble(choice.giant));
        session.stash().physicalInventory(target.run);
        return true;
    }
    private int previous(Giant giant,List<UUID> ids) {
        return shared.stream().filter(s->s.giant==giant && ids.containsAll(s.runs)).mapToInt(Shared::count).sum();
    }
}
