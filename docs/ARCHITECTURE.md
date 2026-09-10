# 笨蛋珈珞架构

## 目标

个人使用、离线优先的 Android 课表。统一身份认证始终发生在武大网页中；应用不采集或保存学号、密码和验证码。

## 模块边界

- `data`：SQLite 数据库和学期、课程、上课安排模型。
- `domain`：教学周与课程实例日期计算，不依赖 Android UI。
- `notification`：每日摘要、课前提醒、开机和时区变化后的恢复。
- `importer`：武大研究生系统适配边界。认证留在 WebView，解析器只处理当前页面或脱敏接口响应。
- `ui`：Compose 今日页、周视图、导入页和设置页。

## 导入策略

1. WebView 仅允许 `whu.edu.cn` 及其子域名的主页面跳转。
2. 用户在学校页面中亲自登录并进入课表。
3. 仅在武大 HTTPS 页面加载开始时注入最小捕获脚本；它只在响应 URL/正文出现课表特征时，于 WebView 内存保留最多 6 份候选。
4. 优先解析课表 JSON/XHR；接口不可用时解析当前页面可见表格，不读取密码输入框值。
5. 自适应字段映射要求课程名、星期与节次同时成立，避免把门户中的无关信息误识别为课程。
6. 解析结果必须先预览，再以单个 SQLite 事务替换本学期数据。
7. 导入成功、主动退出或离开导入页后清除 Cookie、DOM Storage、缓存和 WebView，并重建未来提醒。

当前解析器覆盖常见的中文表头，以及 `KCMC/JSXM/JSMC/XQJ/KSJC/JSJC/ZCD`、`courseName/teacherName/weekDay/startSection` 等常见 JSON 字段。真实系统若使用不同字段，仍需一份脱敏结构扩展映射。不得提交 HAR、Cookie、Token、姓名、学号或实际课程信息到 Git。

## 提醒策略

- 每日 07:30 安排当天摘要。
- 默认课前 15 分钟提醒。
- 有精确闹钟权限时使用精确闹钟，否则降级为允许休眠执行的普通闹钟。
- 初始版本最多预排 300 个未来课程提醒。
- 开机、改时间、改时区、应用升级后重新安排。

## 安全约束

- 禁止明文 HTTP。
- WebView 禁止文件和 Content Provider 访问。
- 不使用 JavaScript bridge；候选响应留在 WebView 页面内存中，只在用户点击识别后通过一次性 `evaluateJavascript` 取回。
- 私有捕获文件只放 `captures/private/`，该目录已被 Git 忽略。
- 所有开发工具、SDK 和 Gradle 缓存位于项目 `.tools/`（E 盘）。
