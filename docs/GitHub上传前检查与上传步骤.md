# GitHub 上传前检查与上传步骤

## 1. 是否可以直接上传整个 001 文件夹

不建议直接在 GitHub 网页里把整个 `project-001-love-travel-map` 文件夹拖上去。

原因：

1. GitHub 网页拖拽不会自动帮你理解 `.gitignore`。
2. 项目里有 `node_modules`、`.venv`、`.next`、`target`、日志、IDE 配置等本地文件。
3. 这些文件体积大、没必要、还可能包含本机路径或运行信息。

推荐方式：

```text
使用 Git 命令或 GitHub Desktop 上传
让 .gitignore 自动过滤不能上传的内容
```

## 2. 当前不能上传的内容

这些已经写入 `.gitignore`，不要上传：

```text
node_modules/
.next/
target/
.venv/
venv/
__pycache__/
.npm-cache/
.idea/
.vscode/
logs/
*.log
.env
.env.*
*.pem
*.key
*.p12
*.jks
```

例外：

```text
.env.example 可以上传
```

`.env.example` 只能写占位符，用来告诉别人需要配置哪些变量，不能写真实密码和密钥。

## 3. 可以上传的核心内容

推荐上传：

```text
apps/front/src/
apps/front/package.json
apps/front/package-lock.json
apps/front/next.config.ts
apps/front/tsconfig.json
apps/front/.env.example

apps/server/src/
apps/server/pom.xml
apps/server/.env.example

apps/ai-service/app/
apps/ai-service/requirements.txt
apps/ai-service/.env.example

docs/
data/
assets/
scripts/
README.md
PRODUCT.md
PROJECT_CONFIG.md
netlify.toml
.gitignore
interview-questions-working.docx
```

## 4. 上传前安全检查

上传前逐项确认：

```text
[ ] 没有真实 OSS AccessKey
[ ] 没有真实 DashScope API Key
[ ] 没有 MySQL 真实密码
[ ] 没有 Redis 真实密码
[ ] 没有 `.env`
[ ] 没有日志文件
[ ] 没有 node_modules
[ ] 没有 .venv
[ ] 没有 target
[ ] 没有 .next
[ ] 没有 IDE 本地配置
```

如果曾经在聊天、截图、文档里暴露过 AccessKey，正式开源前建议在阿里云控制台轮换密钥。

## 5. 推荐上传方式 A：GitHub Desktop

适合新手，图形界面更直观。

步骤：

1. 安装并打开 GitHub Desktop。
2. 登录 GitHub 账号。
3. 选择：

```text
File -> Add local repository
```

4. 选择本地项目路径：

```text
D:\develop\codex-project\project-001-love-travel-map
```

5. 如果提示不是 Git 仓库，选择创建仓库。
6. Repository name 填：

```text
project-001-love-travel-map
```

7. 确认 `.gitignore` 已存在。
8. 在左侧 Changes 里检查文件，不应该出现：

```text
node_modules
.venv
.next
target
.env
logs
.idea
.vscode
```

9. Summary 填：

```text
initial commit
```

10. 点击：

```text
Commit to main
```

11. 点击：

```text
Publish repository
```

12. 如果现在还不想公开源码，先选择 Private。

## 6. 推荐上传方式 B：Git 命令

在项目根目录打开终端：

```powershell
cd D:\develop\codex-project\project-001-love-travel-map
git init
git status
```

检查 `git status` 里不要出现这些：

```text
node_modules/
.venv/
.next/
target/
.env
logs/
.idea/
.vscode/
```

然后提交：

```powershell
git add .
git status
git commit -m "initial commit"
```

去 GitHub 创建一个空仓库，仓库名建议：

```text
project-001-love-travel-map
```

不要勾选自动创建 README、.gitignore、license，避免和本地冲突。

创建后 GitHub 会给你一段 remote 命令，类似：

```powershell
git branch -M main
git remote add origin https://github.com/你的用户名/project-001-love-travel-map.git
git push -u origin main
```

把其中 `你的用户名` 换成你的 GitHub 用户名。

## 7. 不推荐方式：GitHub 网页拖拽上传

不推荐把整个文件夹拖到 GitHub 网页。

如果一定要网页上传，必须手动避开：

```text
node_modules
.venv
.next
target
.npm-cache
.idea
.vscode
logs
.env
```

这个方式容易漏，后续 Netlify 自动部署也不如 Git 仓库方式顺。

## 8. 上传后怎么接 Netlify

GitHub 上传成功后：

```text
Netlify
  -> Add new site
  -> Import an existing project
  -> Deploy with GitHub
  -> 选择 project-001-love-travel-map
```

项目根目录已有 `netlify.toml`，Netlify 会按：

```text
base = apps/front
command = npm run build
publish = .next
```

去构建前端。

