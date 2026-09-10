package com.hanma.echocore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lightweight offline claim extraction. It proposes claims, it does not certify truth. */
public class ClaimGraphEngine {
    private final AscendantStore store;
    public ClaimGraphEngine(AscendantStore s){store=s;}

    public long indexChunk(long sourceId,int part,String text){
        String claim=bestClaim(text);if(claim.isEmpty())return -1;
        long id=store.addClaim(claim,6,sourceId,part);
        try{for(String[] old:store.claims(80)){long oid=parse(old[0]);if(oid==id)continue;String other=old[1];double overlap=tokenOverlap(claim,other);if(overlap<.38)continue;if(opposed(claim,other))store.linkClaims(id,oid,"CONTRADICTS",Math.max(1,(int)Math.round(overlap*5)));else if(overlap>.62)store.linkClaims(id,oid,"RELATED",Math.max(1,(int)Math.round(overlap*4)));}}
        catch(Throwable ignored){}
        return id;
    }
    public String contradictionReport(){StringBuilder b=new StringBuilder();int n=0;for(String[] a:store.claims(120)){for(String[] c:store.claims(120)){if(a==c)continue;if(parse(a[0])>=parse(c[0]))continue;double o=tokenOverlap(a[1],c[1]);if(o>=.45&&opposed(a[1],c[1])){b.append("• ").append(trim(a[1],220)).append("\n  versus: ").append(trim(c[1],220)).append("\n\n");if(++n>=10)return b.toString().trim();}}}return n==0?"No strong heuristic contradiction candidates yet.":b.toString().trim();}

    private static String bestClaim(String text){if(text==null)return "";String clean=text.replace('\n',' ').replaceAll("\\s+"," ").trim();if(clean.length()<45)return "";String[] ss=clean.split("(?<=[.!?])\\s+");String best="";int bestScore=0;for(String s:ss){s=s.trim();if(s.length()<45||s.length()>360)continue;String l=s.toLowerCase(Locale.US);int score=0;if(l.matches(".*\\b(is|are|causes|caused|can|cannot|increases|decreases|produces|requires|shows|suggests|indicates|found|demonstrates|means|results)\\b.*"))score+=3;if(l.matches(".*\\b(always|never|significant|effective|associated|evidence|because|therefore)\\b.*"))score+=1;if(s.matches(".*\\d.*"))score+=1;if(score>bestScore){bestScore=score;best=s;}}return bestScore>=2?best:"";}
    private static boolean opposed(String a,String b){String x=a.toLowerCase(Locale.US),y=b.toLowerCase(Locale.US);String[][] pairs={{" increase "," decrease "},{" increases "," decreases "},{" can "," cannot "},{" is "," is not "},{" are "," are not "},{" effective "," ineffective "},{" supports "," refutes "},{" associated "," unrelated "}};for(String[]p:pairs)if((pad(x).contains(p[0])&&pad(y).contains(p[1]))||(pad(y).contains(p[0])&&pad(x).contains(p[1])))return true;return (x.contains(" no evidence ")&&y.contains(" evidence "))||(y.contains(" no evidence ")&&x.contains(" evidence "));}
    private static double tokenOverlap(String a,String b){ArrayList<String>x=SemanticHasher.tokens(a),y=SemanticHasher.tokens(b);if(x.isEmpty()||y.isEmpty())return 0;int n=0;for(String s:x)if(y.contains(s))n++;return n/(double)Math.max(1,Math.min(x.size(),y.size()));}
    private static long parse(String s){try{return Long.parseLong(s);}catch(Exception e){return 0;}}
    private static String pad(String s){return " "+s.replaceAll("\\s+"," ")+" ";} private static String trim(String s,int n){s=s==null?"":s.trim();return s.length()<=n?s:s.substring(0,n-1)+"…";}
}
