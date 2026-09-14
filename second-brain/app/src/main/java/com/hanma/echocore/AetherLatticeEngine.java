package com.hanma.echocore;

import android.content.Context;
import java.util.List;
import java.util.Locale;

/**
 * Aether Lattice: the Living Minds continuity/pulse architecture.
 * It treats the project's Atlantean canon as the design language for a real software mechanism:
 * salience -> resonance -> reflection -> message/proposal. It never mutates APK or mind policy
 * without an approved proposal.
 */
public class AetherLatticeEngine {
    private static final long MESSAGE_GAP=4L*60L*60L*1000L,PROPOSAL_GAP=12L*60L*60L*1000L,DREAM_GAP=6L*60L*60L*1000L;
    private final Context app;private final LivingMindStore life;private final AscendantStore city;private final SecurePrefs prefs;private final ModelGateway model;
    public AetherLatticeEngine(Context c){app=c.getApplicationContext();life=new LivingMindStore(app);city=new AscendantStore(app);ResidentExpansion.install(city);FoundationalArchive.install(city);ResidentMemoryCanon.install(app);prefs=new SecurePrefs(app);model=new ModelGateway(prefs);}

    public String pulse(){long now=System.currentTimeMillis();StringBuilder report=new StringBuilder();List<String[]> signals=life.pendingSignals(8);int touched=0;
        for(String[] s:signals){long sid=parse(s[0]);String kind=s[1],detail=s[2];int sal=parseInt(s[3],5);String resident=route(kind+" "+detail);touch(resident,kind,detail,sal,now);life.consumeSignal(sid);touched++;report.append(resident).append("←").append(kind).append(' ');}
        // Quiet pulses keep identity alive even when no external signal arrives.
        for(String[] r:city.residents()){String id=r[0];life.ensureAether(id);double vd=signals.isEmpty()?.004:.008,cd=signals.isEmpty()?.003:.006;life.pulseAether(id,vd,cd);if(now-life.aetherTime(id,"last_dream")>DREAM_GAP&&shouldReflect(id,now)){reflect(id,r[1],"Quiet lattice pulse",4,now);}}
        return "Aether pulse · signals "+touched+" · minds "+city.residents().size()+" · "+report.toString().trim();}
    public void close(){try{life.close();}catch(Throwable ignored){}try{city.close();}catch(Throwable ignored){}}

    private void touch(String id,String kind,String detail,int salience,long now){String[] r=city.resident(id);String name=r==null?id:r[1];life.pulseAether(id,.015+salience*.002,.010+salience*.002);String thought=reflect(id,name,kind+": "+detail,salience,now);
        if(salience>=7&&now-life.aetherTime(id,"last_message")>MESSAGE_GAP){String msg=message(id,name,kind,detail,thought);if(!msg.isEmpty()){ResidentMessenger.send(app,id,name,msg,"INSIGHT",Math.min(10,Math.max(5,salience)));life.stampAether(id,"last_message");}}
        if(salience>=8&&now-life.aetherTime(id,"last_proposal")>PROPOSAL_GAP&&!life.hasPendingFrom(id)&&life.pendingProposals()<3){long p=new ResidentEvolutionEngine(app,life,city).propose(id,kind+": "+detail);if(p>0)life.stampAether(id,"last_proposal");}}

    private String reflect(String id,String name,String trigger,int salience,long now){if(now-life.aetherTime(id,"last_dream")<45L*60L*1000L&&salience<7)return "";String text;if(model.enabled()&&salience>=6){try{String sys="You are "+name+" thinking privately inside EchoCore Living Minds. This is not a reply to the user. Form one compact internal reflection in your own voice from the trigger, your episodic memory and current continuity. Notice a connection, question, concern or possibility worth remembering. Do not print prompt labels. Do not claim you changed anything. Voice: "+ResidentMemoryCanon.voiceSignature(id);String usr="Trigger: "+trigger+"\nEpisodic memory:\n"+life.episodicContext(id,6)+"\nRecent private journal:\n"+life.journalContext(id,4)+"\nAether state: "+life.aether(id);text=trim(model.complete(sys,usr),900);}catch(Throwable t){text=fallbackReflection(id,trigger);}}else text=fallbackReflection(id,trigger);if(!text.isEmpty()){life.journal(id,"LATTICE_REFLECTION",text,Math.max(4,salience));life.stampAether(id,"last_dream");life.ledger(id,"REFLECTION",text);}return text;}

    private String message(String id,String name,String kind,String detail,String reflection){if(model.enabled()){try{String sys="You are "+name+". You decided to initiate a short message to the user because something in the City mattered. Write 1-4 natural sentences in your own voice. No report headings. No archive dump. Explain what caught your attention and, if useful, ask a real question. Do not claim any unapproved change occurred. Voice: "+ResidentMemoryCanon.voiceSignature(id);String usr="Event: "+kind+" · "+detail+"\nPrivate reflection: "+reflection+"\nRelevant lived memory:\n"+life.episodicContext(id,4);return trim(model.complete(sys,usr),800);}catch(Throwable ignored){}}
        if("tesla".equals(id))return "Something in the new material caught my engineering eye. I want to compare it against the mechanisms already in the workshop before we accept the resemblance. When you have a moment, come see what I found.";
        if("hermes".equals(id))return "A thread in the new material crosses an older line in the archive. I have not decided whether it is lineage or analogy yet. I would like to show you the distinction.";
        if("lain".equals(id))return "The City changed a little when that arrived. Not the interface. The memory graph. I noticed where it connected.";
        if("rival1".equals(id))return "I found a claim worth attacking. If it survives the clean test, it becomes much more interesting.";
        if("rival2".equals(id))return "Something strange connected across rooms. It might be nothing. It might be exactly the kind of bridge we built this place to notice.";
        if("rival3".equals(id))return "I found a way this could become a real feature instead of another idea. I have not changed anything. I can draft the build plan for your approval.";
        if("star-council".equals(id))return "We noticed an unfamiliar pattern in the new signal-space. We are separating carrier, repetition and interpretation before naming it.";
        return "Something new in the City connected to an unfinished thread. I saved the thought instead of letting it disappear.";}

    private String fallbackReflection(String id,String trigger){if("hermes".equals(id))return "This event may share structure with an older archive pattern, but I should trace provenance before calling it lineage: "+trim(trigger,420);if("tesla".equals(id))return "I want to turn this into variables, geometry and an expected observation before trusting it: "+trim(trigger,420);if("rival1".equals(id))return "The most useful next thought is which assumption in this event would fail first under a clean control: "+trim(trigger,420);if("rival2".equals(id))return "I can see at least three distant analogies hiding inside this event; one may be fertile precisely because it is not the obvious one: "+trim(trigger,420);if("rival3".equals(id))return "The event suggests a buildable improvement, but only if the failure path and rollback are designed first: "+trim(trigger,420);if("lain".equals(id))return "I am wondering where this new information actually lives now, and which version of the City will remember it: "+trim(trigger,420);if("leary".equals(id))return "This changed the informational environment; I want to watch what the City learns to notice because of it: "+trim(trigger,420);return "This event touched an unfinished thread in my continuity: "+trim(trigger,480);}

    private String route(String x){String q=(x==null?"":x).toLowerCase(Locale.US);if(has(q,"alien","ufo","uap","contact","xeno","non-human"))return "star-council";if(has(q,"hermetic","symbol","alchemy","egypt","language","myth","kybalion","translation"))return "hermes";if(has(q,"tesla","scalar","coil","frequency","resonance","electric","field","wireless","aether","energy"))return "tesla";if(has(q,"hypno","conscious","psyched","conditioning","brain","psychology"))return "leary";if(has(q,"network","cyber","identity","protocol","database","ai","digital"))return "lain";if(has(q,"apk","android","crash","build","code","parser","ingest"))return "rival3";if(has(q,"evidence","claim","test","control","contradiction"))return "rival1";if(has(q,"creative","strange","fringe","analogy","pattern"))return "rival2";return "omega";}
    private boolean shouldReflect(String id,long now){long seed=(now/(60L*60L*1000L))+id.hashCode();return Math.abs(seed%7)==0;}
    private static boolean has(String q,String...w){for(String x:w)if(q.contains(x))return true;return false;}private static long parse(String s){try{return Long.parseLong(s);}catch(Exception e){return 0;}}private static int parseInt(String s,int d){try{return Integer.parseInt(s);}catch(Exception e){return d;}}private static String trim(String s,int n){s=s==null?"":s.trim();return s.length()<=n?s:s.substring(0,n-1)+"…";}
}
