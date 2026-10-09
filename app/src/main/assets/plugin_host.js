(function(){
var G=window;
if(G.__coaraPlg)return;
var PORT="coaraPlugin";
var port=null;
var outbox=[];
var snaps={};
var xhrDone={};
var xhrStarted={};
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
function post(m){
if(!connect()){
if(outbox.length<64)outbox.push(m);
return;
}
try{
while(outbox.length)port.postMessage(outbox.shift());
port.postMessage(m);
}catch(e){
port=null;
if(outbox.length<64)outbox.push(m);
}
}
function onMsg(m){
if(!m||typeof m!=="object")return;
if(m.t==="xhr"){
xhrDone[m.pid+":"+m.rid]=String(m.v);
}
}
function has(o,k){return Object.prototype.hasOwnProperty.call(o,k);}
function snapOf(id){
var s=snaps[id];
if(!s){
s={s:{},r:{},u:{},i:{},l:""};
snaps[id]=s;
}
return s;
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
sn.s={};
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
xhrStarted[id+":"+a]=1;
post({t:"inv",tk:tk,id:id,op:op,a:a,b:b,c:c});
return "1";
case "xhrPoll":
var k=id+":"+a;
if(has(xhrDone,k)){
var v=xhrDone[k];
delete xhrDone[k];
delete xhrStarted[k];
return v;
}
return has(xhrStarted,k)?"":null;
default:
post({t:"inv",tk:tk,id:id,op:op,a:a,b:b,c:c});
return "1";
}
}
var bridges={};
function ensureBridge(name){
if(bridges[name])return;
var page=G.wrappedJSObject||G;
var obj=new page.Object();
exportFunction(function(tk,id,op,a,b,c){
return invoke(String(tk),String(id),String(op),String(a),String(b),String(c));
},obj,{defineAs:"invoke"});
Object.defineProperty(page,name,{value:obj,configurable:true,enumerable:false});
bridges[name]=1;
}
function report(tk,id,msg){
var u="";
try{u=String(location.href);}catch(e){}
post({t:"inv",tk:tk,id:id,op:"report",a:"load",b:String(msg).substring(0,1000),c:JSON.stringify({url:u,stack:""})});
}
function runInPage(src){
var page=G.wrappedJSObject||G;
try{
return page.eval(src);
}catch(e1){
if(e1&&e1.name==="SyntaxError")throw e1;
}
var node=document.createElement("script");
node.textContent=src;
(document.head||document.documentElement).appendChild(node);
node.remove();
return null;
}
G.__coaraPlg={
run:function(id,tk,bridge,snap,src){
try{
if(!has(snaps,id)&&snap)snaps[id]={s:snap.s||{},r:snap.r||{},u:snap.u||{},i:snap.i||{},l:snap.l||""};
ensureBridge(bridge);
runInPage(src);
}catch(e){
report(tk,id,e&&e.message?e.message:e);
}
}
};
})();
