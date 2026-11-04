package dev.fragruns.core;

import dev.fragruns.data.DungeonLayout;
import java.util.*;

/** Version 1 heuristic grades, not a calibrated ranking of players. */
public final class RunGrades {
    public record Grade(double difficulty,double skill,int rooms,double effort) {
        public String difficultyText() { return String.format(Locale.ROOT,"%.1f / 10",difficulty); }
        public String skillText() { return Double.isNaN(skill)?"Unavailable":String.format(Locale.ROOT,"%.1f / 10",skill); }
    }
    private static boolean type(DungeonLayout.Room r,String type) {
        return type.equalsIgnoreCase(r.type()) || type.equalsIgnoreCase(r.name());
    }
    public static Grade score(DungeonLayout map,long milliseconds,boolean finished) {
        if(map==null || !map.schematic()) return null;
        var rooms=map.rooms();int n=rooms.size(),start=-1,end=-1;
        int[] owner=new int[36];Arrays.fill(owner,-1);
        for(int i=0;i<n;i++) {
            if(type(rooms.get(i),"ENTRANCE")) { if(start>=0)return null;start=i; }
            if(type(rooms.get(i),"BLOOD")) { if(end>=0)return null;end=i; }
            for(int t:rooms.get(i).tiles()) { if(owner[t]>=0)return null;owner[t]=i; }
        }
        if(start<0 || end<0)return null;
        boolean[][] edges=new boolean[n][n];
        for(var d:map.doors()) {
            int a,b;
            if(d.x()%2==1) {a=(d.y()/2)*6+d.x()/2;b=a+1;}
            else {a=(d.y()/2)*6+d.x()/2;b=a+6;}
            if(a<0 || b>=36 || owner[a]<0 || owner[b]<0)continue;
            edges[owner[a]][owner[b]]=edges[owner[b]][owner[a]]=true;
        }
        double[] distance=new double[n];Arrays.fill(distance,Double.POSITIVE_INFINITY);
        int[] count=new int[n];boolean[] done=new boolean[n];distance[start]=0;
        for(int step=0;step<n;step++) {
            int u=-1;for(int i=0;i<n;i++)if(!done[i] && (u<0 || distance[i]<distance[u]))u=i;
            if(u<0 || !Double.isFinite(distance[u]))break;
            done[u]=true;
            for(int v=0;v<n;v++)if(edges[u][v] && !done[v]) {
                double cost=v==end?0:1+0.5*(rooms.get(v).tiles().size()-1);
                if(distance[u]+cost<distance[v]) {distance[v]=distance[u]+cost;count[v]=count[u]+(v==end?0:1);}
            }
        }
        if(!Double.isFinite(distance[end]))return null;
        double effort=distance[end],difficulty=Math.clamp(1+9*effort/12,0,10);
        double skill=finished && milliseconds>0?Math.clamp(5*(25+8*effort)/(milliseconds/1000.0),0,10):Double.NaN;
        return new Grade(difficulty,skill,count[end],effort);
    }
    public static int luck(Session session) {
        int n=0,known=0,drops=0;double quality=0;
        for(var r:session.runs()) {
            if(!r.completed() || r.giant()==null)continue;
            n++;quality+=r.giant()==Giant.LASR?1:r.giant()==Giant.DIAMANTE?0.8:0;
            if(r.details()!=null && (session.stash().awaiting(r.details().id()) || session.stash().shared(r.details().id())))continue;
            known++;if(r.fragments()>0)drops++;
        }
        // Equal giant probabilities: E[quality]=.45, variance=.2075. Eight prior trials damp tiny samples.
        double spawnZ=(quality-.45*n)/Math.sqrt(.2075*(n+8));
        double dropZ=(drops-.5*known)/Math.sqrt(.25*(known+8));
        return (int)Math.round(50+50*Math.tanh((.65*spawnZ+.35*dropZ)/2));
    }
}
