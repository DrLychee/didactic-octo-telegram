__registerPlugin({
  id:"contacts",
  name:"班干部通讯录",
  icon:"📇",
  navLabel:"通讯录",
  version:"1.0.0",
  author:"官方",
  desc:"基于班级名单维护每位同学的手机号、QQ、宿舍与备注，支持搜索与导出 CSV。",
  html:`<h1>📇 班干部通讯录</h1>
<div class="card">
  <div class="toolbar">
    <input class="search" id="ctSearch" placeholder="🔍 搜索姓名/学号/手机" oninput="renderContacts()">
    <button class="btn-export" onclick="ctExport()">📤 导出通讯录</button>
    <button class="btn-clear" onclick="ctClear()">清空联系方式</button>
  </div>
  <div class="tip" id="ctTip">名单来自「设置 → 学生名单」；在此填写的联系方式保存在本机。</div>
</div>
<div class="card">
  <div style="overflow:auto;max-height:65vh"><table id="ctTable"></table></div>
</div>`,
  init(){
    window.ctLoad=function(){
      try{const v=JSON.parse(localStorage.getItem("plugin_contacts")||"{}");return v&&typeof v==="object"?v:{};}catch(e){return {};}
    };
    window.ctStore=function(o){localStorage.setItem("plugin_contacts",JSON.stringify(o));};

    window.renderContacts=function(){
      const table=document.getElementById("ctTable");
      if(!table)return;
      const students=(typeof STUDENTS!=="undefined"?STUDENTS:[]);
      const data=window.ctLoad();
      const kw=(document.getElementById("ctSearch")||{}).value||"";
      const tip=document.getElementById("ctTip");
      let filled=0;
      students.forEach(function(s){const c=data[s[0]];if(c&&(c.tel||c.qq))filled++;});
      if(tip)tip.innerHTML="名单 "+students.length+" 人，已填联系方式 "+filled+" 人。名单来自「设置 → 学生名单」。";
      if(!students.length){
        table.innerHTML="<tr><td style='padding:26px;color:#64748b;line-height:2'>通讯录依赖班级名单<br>请先到 <b>⚙️ 设置 → 学生名单管理</b> 导入名单</td></tr>";
        return;
      }
      const k=kw.trim();
      const list=students.filter(function(s){
        if(!k)return true;
        const c=data[s[0]]||{};
        return String(s[0]).indexOf(k)>=0||String(s[1]).indexOf(k)>=0||String(c.tel||"").indexOf(k)>=0;
      });
      let html="<tr><th>姓名</th><th>学号</th><th>手机号</th><th>QQ</th><th>宿舍</th><th>备注</th></tr>";
      list.forEach(function(s){
        const id=s[0],c=data[id]||{};
        html+="<tr>"
          +"<td><b>"+esc(s[1])+"</b></td>"
          +"<td style='color:#64748b;font-size:12px'>"+esc(id)+"</td>"
          +"<td><input class='note' style='max-width:120px' value='"+esc(c.tel||"")+"' inputmode='tel' onchange=\"ctSet('"+id+"','tel',this.value)\" placeholder='手机号'></td>"
          +"<td><input class='note' style='max-width:100px' value='"+esc(c.qq||"")+"' inputmode='numeric' onchange=\"ctSet('"+id+"','qq',this.value)\" placeholder='QQ'></td>"
          +"<td><input class='note' style='max-width:90px' value='"+esc(c.room||"")+"' onchange=\"ctSet('"+id+"','room',this.value)\" placeholder='宿舍'></td>"
          +"<td><input class='note' value='"+esc(c.note||"")+"' onchange=\"ctSet('"+id+"','note',this.value)\" placeholder='职务/备注'></td>"
          +"</tr>";
      });
      table.innerHTML=html;
    };

    window.ctSet=function(id,field,val){
      const d=window.ctLoad();
      if(!d[id])d[id]={};
      d[id][field]=String(val||"").trim();
      window.ctStore(d);
      const tip=document.getElementById("ctTip");
      if(tip){
        const students=(typeof STUDENTS!=="undefined"?STUDENTS:[]);
        let filled=0;students.forEach(function(s){const c=d[s[0]];if(c&&(c.tel||c.qq))filled++;});
        tip.innerHTML="名单 "+students.length+" 人，已填联系方式 "+filled+" 人。名单来自「设置 → 学生名单」。";
      }
    };
    window.ctClear=function(){
      askConfirm("确定清空全部联系方式？（名单不受影响）",function(){
        window.ctStore({});window.renderContacts();
      });
    };
    window.ctExport=function(){
      const students=(typeof STUDENTS!=="undefined"?STUDENTS:[]);
      if(!students.length){alert2("名单为空，请先导入名单");return;}
      const d=window.ctLoad();
      const q=function(s){return '"'+String(s||"").replace(/"/g,'""')+'"';};
      let csv="学号,姓名,性别,手机号,QQ,宿舍,备注\n";
      students.forEach(function(s){
        const c=d[s[0]]||{};
        csv+=s[0]+","+q(s[1])+","+(s[2]||"")+","+q(c.tel)+","+q(c.qq)+","+q(c.room)+","+q(c.note)+"\n";
      });
      dl("班级通讯录_"+todayStr()+".csv",csv);
    };
  },
  onShow(){ window.renderContacts(); }
});
