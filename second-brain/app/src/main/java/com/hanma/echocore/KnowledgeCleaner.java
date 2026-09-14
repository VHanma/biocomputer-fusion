package com.hanma.echocore;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Conservative cleanup for OCR-derived text. Raw SourceCatalog chunks remain untouched. */
public final class KnowledgeCleaner {
    private KnowledgeCleaner(){}
    public static class Analysis {public String cleaned="",heading="";public int quality=5;public boolean garbage=false;public long simhash;}

    public static Analysis analyze(String raw){Analysis a=new Analysis();String x=clean(raw);a.cleaned=x;a.heading=heading(x);a.quality=quality(x);a.garbage=a.quality<=2||x.length()<18;a.simhash=SemanticHasher.simhash(x);return a;}
    public static String clean(String raw){
        if(raw==null)return "";String x=Normalizer.normalize(raw,Normalizer.Form.NFKC).replace('\u0000',' ').replace('\u00A0',' ');
        x=x.replaceAll("(?m)([A-Za-z]{2,})-\\s*\\n\\s*([a-z]{2,})","$1$2");
        x=x.replaceAll("(?m)^[ \\t]*[|Il]{1,3}[ \\t]*$","");
        x=x.replaceAll("(?m)^[ \\t]*[-–—_]{3,}[ \\t]*$","");
        x=x.replaceAll("(?m)^[ \\t]*(?:page\\s*)?\\d{1,4}[ \\t]*$","");
        String[] lines=x.split("\\r?\\n");StringBuilder b=new StringBuilder();String prev="";int dup=0;
        for(String line:lines){String l=line.replaceAll("[ \\t]+"," ").trim();if(l.isEmpty()){if(b.length()>0&&b.charAt(b.length()-1)!='\n')b.append('\n');continue;}if(noiseLine(l))continue;String norm=l.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+"," ").trim();if(!norm.isEmpty()&&norm.equals(prev)){if(++dup<=1)continue;}else dup=0;prev=norm;b.append(l).append('\n');}
        return b.toString().replaceAll("\\n{3,}","\n\n").trim();
    }
    public static String heading(String text){if(text==null||text.isEmpty())return "";String[] ls=text.split("\\n");for(int i=0;i<Math.min(6,ls.length);i++){String l=ls[i].trim();if(isHeading(l))return l;}return "";}
    public static boolean isHeading(String l){if(l==null)return false;l=l.trim();if(l.length()<3||l.length()>110)return false;if(l.matches("(?i)^(chapter|part|section|book|volume|lesson|appendix|introduction|preface|foreword|conclusion)\\b.*"))return true;if(l.matches("^[IVXLCDM]{1,8}[.: -]+.+"))return true;int letters=0,upper=0,words=0;for(char c:l.toCharArray()){if(Character.isLetter(c)){letters++;if(Character.isUpperCase(c))upper++;}}for(String w:l.split("\\s+"))if(w.matches(".*[A-Za-z].*"))words++;return words>=2&&words<=12&&letters>=5&&upper>=letters*.78&&!l.endsWith(".");}
    public static int quality(String x){if(x==null||x.isEmpty())return 1;int len=x.length(),letters=0,digits=0,spaces=0,bad=0,repl=0;for(char c:x.toCharArray()){if(Character.isLetter(c))letters++;else if(Character.isDigit(c))digits++;else if(Character.isWhitespace(c))spaces++;else if(c=='�')repl++;else if(!".,;:!?()[]{}'\"/-+%=*&@#$<>°…–—_".contains(String.valueOf(c)))bad++;}double readable=(letters+digits+spaces)/(double)Math.max(1,len),letter=letters/(double)Math.max(1,len);int q=5;if(len>180)q++;if(len>800)q++;if(readable>.90)q++;if(letter>.55)q++;if(repl>0)q-=Math.min(3,repl);if(readable<.70)q-=3;if(letter<.25)q-=2;if(repetitionPenalty(x))q-=2;return Math.max(1,Math.min(10,q));}
    public static double tokenJaccard(String a,String b){Set<String>x=tokens(a),y=tokens(b);if(x.isEmpty()||y.isEmpty())return 0;int inter=0;for(String s:x)if(y.contains(s))inter++;int union=x.size()+y.size()-inter;return union==0?0:inter/(double)union;}
    private static Set<String> tokens(String s){HashSet<String> out=new HashSet<>();for(String w:(s==null?"":s.toLowerCase(Locale.US)).replaceAll("[^a-z0-9]"," ").split("\\s+"))if(w.length()>3)out.add(w);return out;}
    private static boolean noiseLine(String l){if(l.length()>240)return false;int alnum=0,punct=0;for(char c:l.toCharArray()){if(Character.isLetterOrDigit(c))alnum++;else if(!Character.isWhitespace(c))punct++;}if(alnum<2)return true;if(punct>alnum*1.4)return true;return l.matches("(?i)^(copyright|all rights reserved|digitized by|scanned by|downloaded from)\\s*$");}
    private static boolean repetitionPenalty(String x){String low=x.toLowerCase(Locale.US);String[] w=low.replaceAll("[^a-z0-9]"," ").split("\\s+");if(w.length<20)return false;java.util.HashMap<String,Integer> m=new java.util.HashMap<>();int max=0;for(String s:w)if(s.length()>2){int n=m.getOrDefault(s,0)+1;m.put(s,n);max=Math.max(max,n);}return max>w.length*.28;}
}
