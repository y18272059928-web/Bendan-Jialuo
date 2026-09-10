# 参与贡献

欢迎武汉大学同学提交问题和改进。请遵守以下安全规则：

- 不得在 Issue、提交或附件中公开学号、姓名、密码、验证码、Cookie、Token、CAS 票据或完整 HAR。
- 适配新字段时只提供脱敏后的 JSON 字段结构；课程名称、教师、教室等内容请替换为示例值。
- 不要提交 `local.properties`、签名文件、APK、`.tools/`、构建目录或 `captures/private/`。
- 修改导入逻辑后请运行导入器单元测试；修改提醒逻辑后请运行领域测试。

本地验证命令：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

提交问题时请说明手机系统版本、应用版本和可复现步骤；截图前请遮盖个人信息。
