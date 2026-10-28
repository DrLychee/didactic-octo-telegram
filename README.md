# 📋 班级考勤管理系统

通用班级考勤安卓应用（WebView 单页应用），无内置数据 —— 名单与课表全部通过**模板 CSV 导入导出**，任何班级均可直接使用。

## ✨ 功能

- **一键考勤**：按「日期 + 节次」点名，一键全员出勤，单独标记旷课 / 请假 / 迟到，支持备注（请假原因）
- **节次时间**：每节 45 分钟、课间 10 分钟 —— 1-2节 08:00–09:40，3-4节 10:00–11:40，5-6节 14:00–15:40，7-8节 16:00–17:40，自动定位当前节次（可关闭）
- **周历课表**：周一~周日 × 4 节次网格，可手动编辑或按 CSV 模板导入；点击课程格直达该节考勤结果
- **模板化导入导出**：名单模板 / 课表模板 CSV 下载、导出；APP 内文件保存到 `Download/班级考勤/`
- **文件传输面板**：所有下载/导出/导入带进度条，显示保存目录
- **数据管理**：JSON 整体备份 / 恢复、考勤统计导出
- **检查更新**：可配置更新源 version.json，发现新版本提示下载
- **权限合规**：存储权限申请弹窗，拒绝后引导去系统设置授权

## 📱 安装

从 Release 下载 APK 安装（允许"未知来源"）。最低支持 Android 5.0。

## 🔨 从源码构建

需要 Android SDK（build-tools 34 + platform 34）与 JDK 8+：

```bash
# 1. 编译资源与代码
aapt2 compile --dir app/res -o bin/res.zip
aapt2 link -o bin/base.apk -I $ANDROID_JAR --manifest app/AndroidManifest.xml -A app/assets bin/res.zip --java bin
javac -source 8 -target 8 -classpath $ANDROID_JAR -d bin/classes bin/com/lcz/attend/R.java app/src/com/lcz/attend/MainActivity.java

# 2. 转 dex 并打包
d8 --release --lib $ANDROID_JAR --min-api 21 --output bin/dex bin/classes/com/lcz/attend/*.class
# 将 base.apk 内容 + classes.dex 组装为 zip（见 scripts/build.sh）

# 3. 对齐 + 签名
zipalign -f 4 unsigned.apk aligned.apk
apksigner sign --ks your.keystore --out 班级考勤.apk aligned.apk
```

或直接使用 `scripts/build.sh`（自动完成全部步骤，需设置 `ANDROID_HOME`）。

## 📂 目录结构

```
app/
├── AndroidManifest.xml
├── assets/www/index.html   # 全部业务逻辑（纯前端单页，无依赖）
├── res/                    # 图标与字符串
└── src/com/lcz/attend/MainActivity.java  # WebView 壳 + 存储/权限/JS桥
```

## 📋 模板格式

名单（`名单模板.csv`）：

```csv
学号,姓名,性别
2025010101,张三,男
2025010102,李四,女
```

> 也可直接用 Excel/WPS 打开名单 xlsx，全选复制后粘贴到 APP 的导入框，自动识别 Tab / 逗号 / 空格分隔。

课表（`课表模板.csv`）：

```csv
星期,节次,课程,教室
周一,1-2,高等数学,教1-101
周三,3-4,大学英语,教2-205
```

## 🔏 签名说明

发布签名密钥不在仓库中（`*.keystore` 已 gitignore）。升级包必须使用同一密钥签名才能覆盖安装。

## License

MIT
