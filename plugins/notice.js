__registerPlugin({
  id:"notice",
  name:"通知公告",
  icon:"📢",
  navLabel:"公告",
  version:"1.0.0",
  author:"官方",
  desc:"发布班级通知，支持置顶、编辑、删除，按发布时间倒序展示，可导出 CSV。",
  html:`<h1>📢 通知公告</h1>
<div class="card">
  <div class="toolbar">
    <button class="btn-all" onclick="ntAdd()">➕ 发布通知</button>
    <button class="btn-export" onclick="ntExport()">📤 导出通知</button>
    <span class="tip" id="ntCount"></span>
  </div>
  <div class="tip">点击通知可编辑；置顶的通知排在最前。</div>
</div>
<div class="card"><div id="ntList"></div></div>
<style>
.ntmask{display:none;position:fixed;inset:0;background:rgba(15,23,42,.5);z-index:120;align-items:flex-end;justify-content:center;}
.ntmask.show{display:flex;}
.ntform{background:#fff;width:100%;max-width:560px;border-radius:16px 16px 0 0;padding:16px;max-height:90vh;overflow:auto;}
.ntform label{display:block;font-size:13px;color:#475569;margin:11px 0 4px;}
.ntform input,.ntform textarea{width:100%;padding:9px;border:1px solid #cbd5e1;border-radius:8px;font-size:14px;font-family:inherit;}
.ntform textarea{height:150px;resize:vertical;line-height:1.7;}
.ntbody{font-size:13px;line-height:1.75;margin-top:8px;white-space:pre-wrap;color:#334155;word-break:break-word;}
</style>
<div class="ntmask" id="ntMask">
  <div class="ntform">
    <div style="display:flex;justify-content:space-between;align-items:center;">
      <b id="ntTitle" style="font-size:17px">发布通知</b>
      <button class="btn-clear" onclick="ntClose()" style="padding:5px 12px">✕</button>
    </div>
    <label>标题</label><input id="ntTitleIn" placeholder="如：本周五下午班级大扫除">
    <label>内容</label><textarea id="ntBodyIn" placeholder="通知正文…"></textarea>
    <label>发布人</label><input id="ntByIn" placeholder="如：班长 / 团支书">
    <label style="display:flex;align-items:center;gap:8px;margin-top:14px">
      <input type="checkbox" id="ntPinned" style="width:auto"> 置顶显示
    </label>
    <div style="display:flex;gap:10px;margin-top:18px">
      <button class="btn-all" style="flex:1;padding:12px;font-size:15px" onclick="ntSave()">💾 保存</button>
    </div>
  </div>
</div>`,
  init(){
    window.ntLoad=function(){
      try{const v=JSON.parse(localStorage.getItem("plugin_notice")||"[]");return Array.isArray(v)?v:[];}catch(e){return [];}
    };
    window.ntStore=function(list){localStorage.setItem("plugin_notice",JSON.stringify(list));};
    window.ntEditing=null;

    window.renderNotice=function(){
      const list=window.ntLoad().slice().sort(function(a,b){
        if(!!b.pin!==!!a.pin)return b.pin?1:-1;
        return String(b.date+b.id).localeCompare(String(a.date+a.id));
      });
      const c=document.getElementById("ntCount");
      if(c)c.textContent="共 "+list.length+" 条";
      const box=document.getElementById("ntList");
      if(!box)return;
      if(!list.length){box.innerHTML='<div class="lgempty">暂无通知<br>点上方「➕ 发布通知」开始发布</div>';return;}
      box.innerHTML=list.map(function(n){
        return '<div class="lgitem">'
          +'<div class="top"><div><b>'+(n.pin?'📌 ':'')+esc(n.title)+'</b>'
          +'<div class="meta">'+esc(n.date)+(n.by?' · '+esc(n.by):'')+'</div></div>'
          +'<button class="btn-clear" style="font-size:12px;padding:4px 10px" onclick="ntPin(\''+n.id+'\')">'+(n.pin?'取消置顶':'置顶')+'</button></div>'
          +'<div class="ntbody">'+esc(n.body)+'</div>'
          +'<div class="lgacts">'
          +'<button class="btn-del" onclick="ntDel(\''+n.id+'\')">删除</button>'
          +'<button class="btn-export" onclick="ntEdit(\''+n.id+'\')">编辑</button></div></div>';
      }).join("");
    };

    window.ntAdd=function(){
      window.ntEditing=null;
      document.getElementById("ntTitle").textContent="发布通知";
      document.getElementById("ntTitleIn").value="";
      document.getElementById("ntBodyIn").value="";
      document.getElementById("ntByIn").value="";
      document.getElementById("ntPinned").checked=false;
      document.getElementById("ntMask").classList.add("show");
    };
    window.ntEdit=function(id){
      const n=window.ntLoad().filter(function(x){return x.id===id;})[0];
      if(!n)return;
      window.ntEditing=id;
      document.getElementById("ntTitle").textContent="编辑通知";
      document.getElementById("ntTitleIn").value=n.title||"";
      document.getElementById("ntBodyIn").value=n.body||"";
      document.getElementById("ntByIn").value=n.by||"";
      document.getElementById("ntPinned").checked=!!n.pin;
      document.getElementById("ntMask").classList.add("show");
    };
    window.ntClose=function(){document.getElementById("ntMask").classList.remove("show");window.ntEditing=null;};
    window.ntSave=function(){
      const title=document.getElementById("ntTitleIn").value.trim();
      const body=document.getElementById("ntBodyIn").value.trim();
      const by=document.getElementById("ntByIn").value.trim();
      const pin=document.getElementById("ntPinned").checked;
      if(!title){alert2("请填写通知标题");return;}
      let list=window.ntLoad();
      if(window.ntEditing){
        list=list.map(function(n){return n.id===window.ntEditing?Object.assign({},n,{title:title,body:body,by:by,pin:pin}):n;});
      }else{
        list.push({id:"n"+Date.now(),date:todayStr(),title:title,body:body,by:by,pin:pin});
      }
      window.ntStore(list);
      window.ntClose();window.renderNotice();
    };
    window.ntPin=function(id){
      const list=window.ntLoad().map(function(n){return n.id===id?Object.assign({},n,{pin:!n.pin}):n;});
      window.ntStore(list);window.renderNotice();
    };
    window.ntDel=function(id){
      askConfirm("确定删除这条通知？",function(){
        window.ntStore(window.ntLoad().filter(function(n){return n.id!==id;}));
        window.renderNotice();
      });
    };
    window.ntExport=function(){
      const list=window.ntLoad().slice().sort(function(a,b){return String(a.date).localeCompare(String(b.date));});
      if(!list.length){alert2("暂无通知");return;}
      const q=function(s){return '"'+String(s||"").replace(/"/g,'""').replace(/\r?\n/g," ")+'"';};
      let csv="日期,标题,内容,发布人,置顶\n";
      list.forEach(function(n){csv+=n.date+","+q(n.title)+","+q(n.body)+","+q(n.by)+","+(n.pin?"是":"否")+"\n";});
      dl("班级通知_"+todayStr()+".csv",csv);
    };
  },
  onShow(){ window.renderNotice(); }
});
