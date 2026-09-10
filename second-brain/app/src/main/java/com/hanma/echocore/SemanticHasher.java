package com.hanma.echocore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Tiny offline semantic-ish fingerprint used beside FTS. Model embeddings can replace/augment it later. */
public final class SemanticHasher {
    private SemanticHasher(){}
    public static long simhash(String text){
        int[] score=new int[64];
        for(String token:tokens(text)){
            long h=hash64(token);
            int w=Math.min(5,1+token.length()/5);
            for(int i=0;i<64;i++)score[i]+=((h>>>i)&1L)==1L?w:-w;
        }
        long out=0;for(int i=0;i<64;i++)if(score[i]>=0)out|=(1L<<i);return out;
    }
    public static double similarity(long a,long b){return 1.0-(Long.bitCount(a^b)/64.0);}
    public static String ftsQuery(String q){ArrayList<String> t=tokens(q);StringBuilder b=new StringBuilder();int n=0;for(String s:t){if(s.length()<2)continue;if(n++>0)b.append(" OR ");b.append('"').append(s.replace("\"","")).append('"');if(n>=10)break;}return b.toString();}
    public static ArrayList<String> tokens(String text){
        ArrayList<String> out=new ArrayList<>();if(text==null)return out;String s=text.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+"," ");Set<String> seen=new HashSet<>();
        for(String t:s.split("\\s+")){if(t.length()<2||stop(t)||!seen.add(t))continue;out.add(t);if(out.size()>=96)break;}return out;
    }
    private static boolean stop(String w){String s=" the and for are was were with from this that have has had into your you our their they them what when where why how would could should just than then there here about after before while which who whose its it's a an of to in on at by as is be or if ";return s.contains(" "+w+" ");}
    private static long hash64(String s){try{byte[] d=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));long x=0;for(int i=0;i<8;i++)x=(x<<8)|(d[i]&255L);return x;}catch(Exception e){return s.hashCode()*0x9E3779B97F4A7C15L;}}
}
