# 后续部署准备

当前只准备配置，没有创建GitHub仓库或部署公网。功能验收后再选择仓库、服务器/托管平台、域名和预算。

## GitHub

上传时只包含源代码、迁移脚本、依赖锁文件、测试、合成数据截图与说明。`.tools`、`data`、`target`、`node_modules`、日志、上传照片和 `.env` 不上传。

已准备 `.github/workflows/ci.yml`：启动临时MySQL，运行后端测试、构建Vue、启动测试后端并用Chromium执行浏览器测试。它尚未在GitHub运行，首次推送后需要检查结果。

## MySQL 本地运行

准备独立数据库和用户后，在后端启动终端设置 DB_URL、DB_USERNAME、DB_PASSWORD，并运行：

```powershell
.\scripts\dev.cmd run -Profiles 'dev,mysql'
```

此命令使用MySQL但仍初始化开发演示账号。正式服务必须使用 `prod,mysql`，不能使用dev。H2已有开发数据不会自动复制到MySQL；正式部署初始化新库，真实数据迁移另行准备导出与校验。

## Docker 方案

准备了 Dockerfile、Dockerfile.web、compose.yml 和 Nginx 配置。当前机器没有Docker，未实测镜像构建和Compose启动，部署前需要完成这一步。

复制 `.env.example` 为 `.env` 并替换数据库、管理员密码与JWT密钥；JWT密钥使用32字节以上安全随机数的Base64编码。可选配置DeepSeek密钥，不能提交到仓库。

```powershell
$jwtBytes = New-Object byte[] 32
$generator = [Security.Cryptography.RandomNumberGenerator]::Create()
$generator.GetBytes($jwtBytes)
[Convert]::ToBase64String($jwtBytes)
$generator.Dispose()
```

上面的结果只在本机填进 .env，生产JWT密钥应持久保存，不能每次启动随机生成。

后续安装Docker后：

```text
docker compose up -d --build
```

默认网页仅监听本机8088。MySQL和后端不映射到公网端口。命名卷持久保存数据库与照片；不要执行带 `-v` 的停止命令删除数据卷。

## 生产配置

生产模式要求MySQL、JWT密钥和12字符以上管理员密码。首次启动初始化管理员，之后不会自动重设密码。正式前端关闭演示登录按钮，后端不创建开发用户。

实际公网方案还需要域名/HTTPS、备份恢复演练、日志与监控、模型额度限制、图片存储方案和实际Docker验证。当前限速为单实例内存IP窗口；多实例需共享限流。Nginx覆盖转发身份头，不信任客户端自带转发头；后端应只在内部网络暴露。

生产配置已在独立MySQL测试库验证能初始化一个管理员并登录；前端生产构建已通过Chrome实际页面访问。公网、Docker与真实DeepSeek调用尚未验证。
