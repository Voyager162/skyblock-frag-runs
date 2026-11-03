package dev.fragruns.data;

import dev.fragruns.core.ModConfig;
import dev.fragruns.core.Session;
import dev.fragruns.core.FragmentStash;
import dev.fragruns.core.Giant;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

public final class SessionStore {
    private final Path directory;
    private final RunMaps maps;
    private String warning = "";
    private java.util.Set<String> stashArchives;
    private List<FragmentStash.Choice> stashChoices;
    public SessionStore(Path directory) { this.directory = directory; maps = new RunMaps(directory.resolve("maps")); }
    public RunMaps maps() { return maps; }
    public Path directory() { return directory; }
    public String warning() { return warning; }
    public ModConfig loadConfig() {
        Path path = directory.resolve("settings.json");
        if (Files.exists(path)) {
            try { ModConfig c = JsonFiles.read(path, ModConfig.class); c.sanitize(); return c; }
            catch (IOException e) { preserve(path); warning = "Settings recovered to defaults; original file preserved."; }
        }
        return new ModConfig();
    }
    public void loadSession(Session session) {
        Path path = directory.resolve("session.json");
        if (!Files.exists(path)) return;
        try { session.restore(JsonFiles.read(path, Session.Snapshot.class)); }
        catch (IOException | IllegalArgumentException e) {
            preserve(path);
            try {
                session.restore(JsonFiles.read(directory.resolve("session.json.bak"), Session.Snapshot.class));
                warning = "Session recovered from backup; damaged save preserved.";
            } catch (IOException | IllegalArgumentException fallback) {
                warning = "Could not restore the last session; damaged save preserved.";
            }
        }
    }
    private void preserve(Path path) {
        try { Files.copy(path, path.resolveSibling(path.getFileName()+".corrupt-"+System.currentTimeMillis())); }
        catch (IOException e) { warning = "Could not preserve damaged save: " + e.getMessage(); }
    }
    public void saveConfig(ModConfig config) { config.sanitize(); save(directory.resolve("settings.json"), config); }
    public record StashStock(java.util.Map<Giant,Integer> balances,java.util.Map<Giant,Integer> old,boolean knownEmpty) {
        public StashStock { balances=java.util.Map.copyOf(balances);old=java.util.Map.copyOf(old); }
    }
    private Path stockFile(String scope) {
        var key=java.util.UUID.nameUUIDFromBytes(scope.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return directory.resolve("stash").resolve(key+".json");
    }
    public StashStock loadStashStock(String scope) {
        var file=stockFile(scope);
        if (Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) try { return JsonFiles.read(file,StashStock.class); }
        catch (IOException e) { warning="Could not read the previous stash balances."; }
        return new StashStock(java.util.Map.of(),java.util.Map.of(),false);
    }
    public void saveStashStock(String scope,StashStock stock) { save(stockFile(scope),stock); }
    public void saveSession(Session session) {
        if (!session.id().isBlank()) save(directory.resolve("session.json"), session.snapshot());
    }
    public void archive(Session session) {
        try { archiveChecked(session); }
        catch (IOException e) { warning = "Could not archive session: " + e.getMessage(); }
    }
    public void archiveChecked(Session session) throws IOException {
        if (!session.id().isBlank()) {
            JsonFiles.write(archivePath(session.id()), session.snapshot());
            indexStash(session);
        }
    }
    private void indexStash(Session s) {
        if (stashArchives==null) return;
        if (s.stash().outstanding()) stashArchives.add(s.id()); else stashArchives.remove(s.id());
    }
    /** Only archives with unresolved stash evidence need opening after the initial index. */
    private List<Session> stashSessions(Session live) throws IOException {
        var sessions=new ArrayList<Session>(); sessions.add(live);
        if (stashArchives==null) {
            var found=new java.util.LinkedHashSet<String>();
            Path history=directory.resolve("history");
            if (Files.isDirectory(history,LinkOption.NOFOLLOW_LINKS)) try (var paths=Files.newDirectoryStream(history,"*.json")) {
                for (Path file:paths) {
                    String name=file.getFileName().toString(), id=name.substring(0,name.length()-5);
                    if (id.equals(live.id()) || !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) continue;
                    try {
                        Session s=readArchived(id);
                        if (s.stash().outstanding()) {found.add(id);sessions.add(s);}
                    } catch (IOException e) { warning="Could not check an unreadable session for stash drops."; }
                }
            }
            stashArchives=found;
            indexStash(live);
        } else for (String id:List.copyOf(stashArchives)) {
            if (!id.equals(live.id())) sessions.add(readArchived(id));
        }
        return sessions;
    }
    public boolean hasStashEvidence(Session live,String scope,Giant giant) throws IOException {
        return stashSessions(live).stream().anyMatch(s->s.stash().hasEvidence(s,scope,giant));
    }
    public FragmentStash.Result settleStash(Session live,String scope,java.util.Map<Giant,Integer> received,
                                             boolean emptied,boolean fromBalance,java.util.function.ToDoubleFunction<Giant> price) throws IOException {
        var result=FragmentStash.settle(stashSessions(live),scope,received,emptied,fromBalance,choices(),price);
        if (!result.choices().isEmpty()) {
            var all=new ArrayList<>(choices());all.addAll(result.choices());saveChoices(all);
        }
        for (Session s:result.changed()) saveStashSession(live,s);
        return result;
    }
    private void saveStashSession(Session live,Session s) throws IOException {
        if (s==live) JsonFiles.write(directory.resolve("session.json"),s.snapshot());
        if (s!=live || !s.active()) archiveChecked(s);
    }
    public FragmentStash.Result applyHubBalances(Session live,java.util.UUID auditId,java.util.Map<FragmentStash.Target,Boolean> interval,
            java.util.Map<Giant,Integer> gains,java.util.function.ToDoubleFunction<Giant> price) throws IOException {
        var sessions=new java.util.HashMap<String,Session>();
        for (var target:interval.keySet()) if (!sessions.containsKey(target.session())) {
            if (target.session().equals(live.id())) sessions.put(target.session(),live);
            else if (Files.isRegularFile(archivePath(target.session()),LinkOption.NOFOLLOW_LINKS))
                sessions.put(target.session(),readArchived(target.session()));
        }
        var result=FragmentStash.measured(auditId,sessions,interval,gains,price);
        if (!result.choices().isEmpty()) {
            var all=new ArrayList<>(choices());
            for (var choice:result.choices()) if (all.stream().noneMatch(c->c.id().equals(choice.id()))) all.add(choice);
            saveChoices(all);
        }
        for (var s:result.changed()) saveStashSession(live,s);
        return result;
    }
    private List<FragmentStash.Choice> choices() throws IOException {
        if (stashChoices==null) {
            Path file=directory.resolve("stash-choices.json");
            stashChoices=Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)?List.of(JsonFiles.read(file,FragmentStash.Choice[].class)):List.of();
        }
        return stashChoices;
    }
    private void saveChoices(List<FragmentStash.Choice> list) throws IOException {
        JsonFiles.write(directory.resolve("stash-choices.json"),list);stashChoices=List.copyOf(list);
    }
    public boolean hasStashChoice(String id,java.util.UUID run) {
        var target=new FragmentStash.Target(id,run);
        try { return choices().stream().anyMatch(c->c.accepts(target)); }
        catch (IOException e) { warning="Could not read pending stash receipts."; return false; }
    }
    public Session assignStash(Session live,String id,java.util.UUID run,java.util.function.ToDoubleFunction<Giant> price) throws IOException {
        var target=new FragmentStash.Target(id,run);
        var all=new ArrayList<>(choices());
        var choice=all.stream().filter(c->c.accepts(target)).findFirst().orElseThrow(()->new IOException("Receipt already assigned"));
        int index=all.indexOf(choice);
        Session selected=id.equals(live.id())?live:readArchived(id);
        var locked=new FragmentStash.Choice(choice.id(),choice.giant(),choice.targets(),target,choice.normalExits());
        // Pin the destination before crediting. Retrying after a crash cannot award the same receipt to another run.
        all.set(index,locked); saveChoices(all);
        if (!FragmentStash.applyChoice(selected,locked,target,price)) throw new IOException("Run unavailable");
        saveStashSession(live,selected);
        all.remove(index);saveChoices(all);
        return selected;
    }
    private Path archivePath(String id) throws IOException {
        if (id == null || !id.matches("[a-zA-Z0-9._-]{1,128}")) throw new IOException("Invalid session ID");
        Path history = directory.resolve("history");
        if (Files.exists(history,LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(history,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Unexpected history folder");
        return history.resolve(id+".json");
    }
    public record Summary(String id,long elapsedMs,int runs) {
        public static Summary of(Session s) { return new Summary(s.id(),s.elapsedMs(),s.totalRuns()); }
        public Instant started() {
            try { return Instant.parse(id.replaceFirst("T(\\d{2})-(\\d{2})-(\\d{2})","T$1:$2:$3")); }
            catch (DateTimeParseException e) { return Instant.EPOCH; }
        }
    }
    public record Catalog(List<Summary> sessions,int unreadable) {}
    public Catalog catalog() throws IOException {
        Path history = directory.resolve("history");
        if (!Files.exists(history,LinkOption.NOFOLLOW_LINKS)) return new Catalog(List.of(),0);
        if (!Files.isDirectory(history,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Unexpected history folder");
        var sessions = new ArrayList<Summary>();
        int unreadable = 0;
        try (var paths = Files.newDirectoryStream(history,"*.json")) {
            for (Path file : paths) {
                if (!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) continue;
                String name = file.getFileName().toString();
                try { sessions.add(Summary.of(readArchived(name.substring(0,name.length()-5)))); }
                catch (IOException e) { unreadable++; }
            }
        }
        sessions.sort(Comparator.comparing(Summary::started).reversed().thenComparing(Summary::id));
        return new Catalog(List.copyOf(sessions),unreadable);
    }
    public Session readArchived(String id) throws IOException {
        Path file = archivePath(id);
        try { return readArchive(file,id); }
        catch (IOException primary) {
            if (!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) throw primary;
            return readArchive(file.resolveSibling(file.getFileName()+".bak"),id);
        }
    }
    private Session readArchive(Path file,String id) throws IOException {
        if (!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Session unavailable");
        var loaded = Session.live();
        try { loaded.restore(JsonFiles.read(file,Session.Snapshot.class)); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid session",e); }
        if (!id.equals(loaded.id())) throw new IOException("Session ID mismatch");
        return loaded;
    }
    /** Commit the outgoing archive and incoming checkpoint before replacing any live state. */
    public void continueSession(Session live,String id) throws IOException {
        if (live.id().equals(id)) {
            if (id.isBlank()) throw new IOException("No session selected");
            if (!live.running()) {
                JsonFiles.write(directory.resolve("session.json"),live.snapshot());
                live.resumeSaved();
            }
            return;
        }
        var selected = readArchived(id).snapshot();
        if (!live.id().isBlank()) { JsonFiles.write(archivePath(live.id()),live.switchingAway()); indexStash(live); }
        JsonFiles.write(directory.resolve("session.json"),selected);
        live.restore(selected);
        live.resumeSaved();
    }
    private static final Pattern ARCHIVE_FILE = Pattern.compile(".+\\.json(?:\\.(?:bak|tmp|corrupt-\\d+))?");
    private static final Pattern CHECKPOINT = Pattern.compile("session\\.json(?:\\.(?:bak|tmp|corrupt-\\d+))?");

    /** Clear this profile's past sessions. A live session keeps its in-memory state and a current checkpoint. */
    public void clearHistory(Session session) throws IOException {
        stashArchives=null;
        if (Files.isDirectory(directory)) deleteSessionFiles(directory,Pattern.compile("stash-choices\\.json(?:\\.(?:bak|tmp))?"),false);
        stashChoices=List.of();
        Path history = directory.resolve("history");
        if (Files.exists(history, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(history, LinkOption.NOFOLLOW_LINKS)
                    || !history.toRealPath().startsWith(directory.toRealPath())) {
                throw new IOException("Unexpected history folder");
            }
            deleteSessionFiles(history, ARCHIVE_FILE, false);
        }
        if (session.active()) JsonFiles.write(directory.resolve("session.json"), session.snapshot());
        var keep = new HashSet<java.util.UUID>();
        if (session.active()) {
            for (var r : session.runs()) if (r.details()!=null) keep.add(r.details().id());
            if (session.currentRunId()!=null) keep.add(session.currentRunId());
        }
        maps.clearExcept(keep);
        if (Files.isDirectory(directory)) deleteSessionFiles(directory, CHECKPOINT, true);
        if (!session.active()) {
            // Last: a failed operation remains visible and can be retried.
            Files.deleteIfExists(directory.resolve("session.json"));
            session.clearSaved();
        }
    }

    private static void deleteSessionFiles(Path folder, Pattern names, boolean keepCheckpoint) throws IOException {
        try (var paths = Files.newDirectoryStream(folder)) {
            for (Path path : paths) {
                String name = path.getFileName().toString();
                if (keepCheckpoint && name.equals("session.json")) continue;
                if (names.matcher(name).matches() && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
    private void save(Path file, Object value) {
        try { JsonFiles.write(file, value); }
        catch (IOException e) { warning = "Save failed: " + e.getMessage(); }
    }
}
