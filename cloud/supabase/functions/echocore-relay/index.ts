import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabase=createClient(Deno.env.get("SUPABASE_URL")!,Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
const enc=new TextEncoder();
async function hash(s:string){const b=await crypto.subtle.digest("SHA-256",enc.encode(s));return Array.from(new Uint8Array(b)).map(x=>x.toString(16).padStart(2,"0")).join("");}
function j(data:any,status=200){return new Response(JSON.stringify(data),{status,headers:{"content-type":"application/json","access-control-allow-origin":"*","access-control-allow-headers":"content-type,x-device-secret,x-device-id","cache-control":"no-store"}});}
function id(){return crypto.randomUUID().replaceAll("-","");}
function cleanText(v:any,n=4000){const s=String(v??"");return s.length>n?s.slice(0,n):s;}
async function auth(deviceId:string,secret:string){
 if(!deviceId||!secret)return "";
 const h=await hash(secret);
 const {data:requested}=await supabase.from("echocore_devices").select("device_id,device_secret_hash,app_version,created_at,last_seen").eq("device_id",deviceId).maybeSingle();
 if(requested&&requested.device_secret_hash===h)return deviceId;
 if(!requested)return "";
 const {data:twin}=await supabase.from("echocore_devices").select("device_id,device_secret_hash,app_version,created_at,last_seen").eq("device_secret_hash",h).maybeSingle();
 if(!twin||twin.device_id===deviceId||twin.app_version!==requested.app_version)return "";
 const dt=Math.abs(new Date(twin.created_at).getTime()-new Date(requested.created_at).getTime());
 if(!Number.isFinite(dt)||dt>5000)return "";
 const reqSeen=requested.last_seen?new Date(requested.last_seen).getTime():0;
 const twinSeen=twin.last_seen?new Date(twin.last_seen).getTime():0;
 return twinSeen>reqSeen?twin.device_id:requested.device_id;
}

Deno.serve(async(req)=>{
 if(req.method==="OPTIONS")return j({ok:true});
 const u=new URL(req.url);const p=u.pathname.split("/").filter(Boolean).pop()||"";
 try{
  if(p==="health")return j({ok:true,service:"EchoCore Relay",version:3,auth_repair:true,time:new Date().toISOString()});
  if(p==="register"&&req.method==="POST"){
   const b=await req.json().catch(()=>({}));const device_id=id();const device_secret=id()+id();
   const {error}=await supabase.from("echocore_devices").insert({device_id,device_secret_hash:await hash(device_secret),app_version:cleanText(b.app_version,64),capabilities:Array.isArray(b.capabilities)?b.capabilities.slice(0,64):[],health:{state:"registered",relay_version:3}});if(error)throw error;
   return j({ok:true,device_id,device_secret,relay_version:3});
  }
  const b=req.method==="POST"?await req.json().catch(()=>({})):{};
  let deviceId=req.headers.get("x-device-id")||b.device_id||u.searchParams.get("device_id")||"";
  const secret=req.headers.get("x-device-secret")||b.device_secret||"";
  const canonical=await auth(deviceId,secret);if(!canonical)return j({ok:false,error:"unauthorized"},401);deviceId=canonical;

  if(p==="heartbeat"&&req.method==="POST"){
   const health=(b.health&&typeof b.health==="object")?b.health:{};
   await supabase.from("echocore_devices").update({last_seen:new Date().toISOString(),app_version:b.app_version?cleanText(b.app_version,64):undefined,capabilities:Array.isArray(b.capabilities)?b.capabilities.slice(0,64):undefined,health,last_error:b.last_error?cleanText(b.last_error,2000):null}).eq("device_id",deviceId);
   return j({ok:true,relay_version:3});
  }

  if(p==="telemetry"&&req.method==="POST"){
   const kind=cleanText(b.kind||"event",64);const payload=(b.payload&&typeof b.payload==="object")?b.payload:{message:cleanText(b.payload,4000)};
   await supabase.from("echocore_device_events").insert({device_id:deviceId,kind,payload});
   await supabase.from("echocore_devices").update({last_telemetry_at:new Date().toISOString(),last_error:kind.includes("error")||kind.includes("crash")?cleanText(payload?.message||payload?.summary||kind,2000):undefined}).eq("device_id",deviceId);
   return j({ok:true});
  }

  if(p==="poll"&&req.method==="POST"){
   const stale=new Date(Date.now()-90000).toISOString();
   await supabase.from("echocore_commands").update({status:"pending",claimed_at:null,last_error:"claim timeout; safely retried"}).eq("device_id",deviceId).eq("status","claimed").lt("claimed_at",stale).lt("attempts",4);
   await supabase.from("echocore_commands").update({status:"failed",last_error:"retry limit exceeded"}).eq("device_id",deviceId).eq("status","claimed").lt("claimed_at",stale).gte("attempts",4);
   await supabase.from("echocore_commands").update({status:"expired",last_error:"command expired"}).eq("device_id",deviceId).in("status",["pending","claimed"]).lt("expires_at",new Date().toISOString());
   const {data,error}=await supabase.from("echocore_commands").select("id,command,created_at,attempts").eq("device_id",deviceId).eq("status","pending").order("created_at").limit(6);if(error)throw error;
   if(data?.length){const ids=data.map((x:any)=>x.id);await supabase.from("echocore_commands").update({status:"claimed",claimed_at:new Date().toISOString()}).in("id",ids);for(const row of data){await supabase.from("echocore_commands").update({attempts:(row.attempts||0)+1}).eq("id",row.id);}}
   return j({ok:true,commands:data||[],relay_version:3});
  }

  if(p==="respond"&&req.method==="POST"){
   if(!b.command_id)return j({ok:false,error:"missing_command_id"},400);
   const {error}=await supabase.from("echocore_commands").update({status:"done",completed_at:new Date().toISOString(),response:b.response??null,last_error:null}).eq("id",b.command_id).eq("device_id",deviceId);if(error)throw error;return j({ok:true});
  }

  if(p==="command"&&req.method==="POST"){
   if(!b.command)return j({ok:false,error:"missing_command"},400);
   const {data,error}=await supabase.from("echocore_commands").insert({device_id:deviceId,command:b.command,expires_at:new Date(Date.now()+600000).toISOString()}).select("id").single();if(error)throw error;return j({ok:true,command_id:data.id});
  }

  if(p==="result"&&req.method==="POST"){
   if(!b.command_id)return j({ok:false,error:"missing_command_id"},400);
   const {data,error}=await supabase.from("echocore_commands").select("status,response,created_at,claimed_at,completed_at,attempts,last_error").eq("id",b.command_id).eq("device_id",deviceId).maybeSingle();if(error)throw error;return j({ok:true,result:data});
  }
  return j({ok:false,error:"not_found"},404);
 }catch(e){return j({ok:false,error:cleanText((e as any)?.message||e,3000)},500);}
});