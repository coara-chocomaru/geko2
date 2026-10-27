(function(){
var G=window;
if(G.__coaraPlg)return;
var PAGE=G.wrappedJSObject||G;
var PAGE_EVAL=typeof PAGE.eval==="function"?PAGE.eval:null;
var PORT="coaraPlugin";
var port=null;
var outbox=[];
var outboxChars=0;
var MAX_OUTBOX_ITEMS=64;
var MAX_OUTBOX_CHARS=4194304;
var snaps=Object.create(null);
var xhrDone=Object.create(null);
var xhrStarted=Object.create(null);
var xhrDoneChars=0;
var MAX_XHR_STARTED=64;
var MAX_XHR_DONE_CHARS=16777216;
var bridges=Object.create(null);
var commandHandlers=Object.create(null);
var tempSequence=0;
function connect(){
if(port)return true;
try{
port=browser.runtime.connectNative(PORT);
port.onMessage.addListener(onMsg);
port.onDisconnect.addListener(function(){port=null;});
return true;
}catch(e){
port=null;
return false;
}
}
function messageSize(m){
return 128+(typeof m.a==="string"?m.a.length:0)+(typeof m.b==="string"?m.b.length:0)+(typeof m.c==="string"?m.c.length:0)+(typeof m.originUrl==="string"?m.originUrl.length:0);
}
function queueMessage(m){
var size=messageSize(m);
if(outbox.length>=MAX_OUTBOX_ITEMS||size>MAX_OUTBOX_CHARS||outboxChars+size>MAX_OUTBOX_CHARS)return false;
outbox.push({m:m,s:size});
outboxChars+=size;
return true;
}
function post(m){
if(m&&m.t==="inv"&&m.op==="xhr"){
try{m.originUrl=String(G.location.href).substring(0,8193);}catch(e){m.originUrl="";}
}
if(messageSize(m)>MAX_OUTBOX_CHARS)return false;
if(!connect())return queueMessage(m);
try{
while(outbox.length){
var q=outbox[0];
port.postMessage(q.m);
outbox.shift();
outboxChars=Math.max(0,outboxChars-q.s);
}
port.postMessage(m);
return true;
}catch(e){
port=null;
return queueMessage(m);
}
}
function onMsg(m){
if(!m||typeof m!=="object")return;
if(m.t==="cmd"){
var fn=commandHandlers[String(m.id||"")];
if(typeof fn==="function"){
try{fn();}catch(e){}
}
return;
}
if(m.t!=="xhr")return;
var k=String(m.pid)+":"+String(m.rid);
if(!has(xhrStarted,k))return;
var old=has(xhrDone,k)?xhrDone[k]:"";
var next=xhrDoneChars-old.length;
var v=String(m.v);
if(next+v.length>MAX_XHR_DONE_CHARS)v="{\"error\":\"response retention limit\"}";
xhrDone[k]=v;
xhrDoneChars=next+v.length;
}
function has(o,k){return Object.prototype.hasOwnProperty.call(o,k);}
function copyMap(value){
var out=Object.create(null);
if(value&&typeof value==="object"){
var keys=Object.keys(value);
for(var i=0;i<keys.length;i++)out[keys[i]]=value[keys[i]];
}
return out;
}
function snapOf(id){
var s=snaps[id];
if(!s){
s={s:Object.create(null),r:Object.create(null),u:Object.create(null),i:Object.create(null),l:""};
snaps[id]=s;
}
return s;
}
function report(tk,id,message){
var href="";
try{href=String(G.location.href).substring(0,8192);}catch(e){}
try{invoke(tk,id,"report","runtime",String(message==null?"":message).substring(0,1000),JSON.stringify({url:href,stack:""}));}catch(e){}
}
function invoke(tk,id,op,a,b,c){
var sn=snapOf(id);
switch(op){
case "sget":
return has(sn.s,a)?sn.s[a]:null;
case "sset":
sn.s[a]=String(b);
post({t:"inv",tk:tk,id:id,op:op,a:a,b:b,c:c});
return "1";
case "sdel":
delete sn.s[a];
post({t:"inv",tk:tk,id:id,op:op,a:a,b:b,c:c});
return "1";
case "skeys":
return JSON.stringify(Object.keys(sn.s));
case "sclear":
sn.s=Object.create(null);
post({t:"inv",tk:tk,id:id,op:op,a:a,b:b,c:c});
return "1";
case "res":
return has(sn.r,a)?sn.r[a]:null;
case "resurl":
return has(sn.u,a)?sn.u[a]:null;
case "i18n":
if(!has(sn.i,a))return "";
var msg=sn.i[a];
if(b){
try{
var subs=JSON.parse(b);
msg=msg.replace(/\$([1-9])/g,function(x,n){var v=subs[Number(n)-1];return v==null?"":String(v);});
}catch(e){}
}
return msg;
case "uilang":
return sn.l;
case "xhr":
var sk=id+":"+a;
if(!has(xhrStarted,sk)&&Object.keys(xhrStarted).length>=MAX_XHR_STARTED)return "0";
xhrStarted[sk]=1;
if(!post({t:"inv",tk:tk,id:id,op:op,a:a,b:b,c:c})){
delete xhrStarted[sk];
return "0";
}
return "1";
case "xhrPoll":
var k=id+":"+a;
if(has(xhrDone,k)){
var v=xhrDone[k];
xhrDoneChars=Math.max(0,xhrDoneChars-v.length);
delete xhrDone[k];
delete xhrStarted[k];
return v;
}
return has(xhrStarted,k)?"":null;
case "xhrAbort":
var ak=id+":"+a;
if(has(xhrDone,ak)){
xhrDoneChars=Math.max(0,xhrDoneChars-xhrDone[ak].length);
delete xhrDone[ak];
}
delete xhrStarted[ak];
post({t:"inv",tk:tk,id:id,op:op,a:a,b:b,c:c});
return "1";
default:
post({t:"inv",tk:tk,id:id,op:op,a:a,b:b,c:c});
return "1";
}
}
function clearCommands(name){
var entry=bridges[name];
if(!entry)return;
var group=entry.commands;
for(var cid in group){
if(has(group,cid)){
delete commandHandlers[cid];
delete group[cid];
}
}
}
function ensureBridge(name,tk,id){
if(has(bridges,name))return bridges[name].object;
var page=PAGE;
var obj=new page.Object();
var group=Object.create(null);
exportFunction(function(op,a,b,c){
return invoke(tk,id,String(op),a==null?"":String(a),b==null?"":String(b),c==null?"":String(c));
},obj,{defineAs:"invoke"});
exportFunction(function(cid,fn){
cid=String(cid==null?"":cid);
if(!cid||cid.length>128||typeof fn!=="function")return false;
if(Object.keys(commandHandlers).length>=256&&!has(commandHandlers,cid))return false;
commandHandlers[cid]=fn;
group[cid]=1;
return true;
},obj,{defineAs:"registerCommand"});
exportFunction(function(cid){
cid=String(cid==null?"":cid);
if(has(group,cid))delete group[cid];
delete commandHandlers[cid];
return true;
},obj,{defineAs:"unregisterCommand"});
bridges[name]={object:obj,commands:group};
return obj;
}
function tempKey(){
tempSequence++;
return "__coaraBridgeTemp_"+tempSequence.toString(36)+"_"+Date.now().toString(36)+"_"+Math.random().toString(36).slice(2,10);
}
function runInPage(src,bridgeObject){
var page=PAGE;
var key=tempKey();
Object.defineProperty(page,key,{value:bridgeObject,enumerable:false,configurable:true});
var wrapped="(function(w,k){var b;try{b=w[k];delete w[k];}catch(e){}return (function(__coaraBridge){\n"+String(src)+"\n})(b);})(window,"+JSON.stringify(key)+");";
try{
if(!PAGE_EVAL)throw new Error("page evaluator unavailable");
return PAGE_EVAL(wrapped);
}finally{
try{delete page[key];}catch(e){}
}
}
G.__coaraPlg={
run:function(id,tk,bridge,snap,src){
try{
if(!has(snaps,id)&&snap)snaps[id]={s:copyMap(snap.s),r:copyMap(snap.r),u:copyMap(snap.u),i:copyMap(snap.i),l:snap.l||""};
var bridgeKey=String(bridge);
var bridgeObject=ensureBridge(bridgeKey,String(tk),String(id));
clearCommands(bridgeKey);
runInPage(src,bridgeObject);
}catch(e){
report(tk,id,e&&e.message?e.message:e);
}
}
};
})();
