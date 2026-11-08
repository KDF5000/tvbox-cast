# 官网部署

官网是静态 HTML、CSS 和 JavaScript，源文件位于 `docs/site`。

## Vercel

导入本仓库时使用以下配置：

- Root Directory：`docs/site`
- Framework Preset：Other
- Build Command：不需要
- Install Command：不需要
- Output Directory：`.`
- Production Branch：`main`

`docs/site/vercel.json` 已声明静态部署配置。项目连接 GitHub 后，推送到 `main` 会更新正式站点，其他分支生成预览。

部署前运行：

```sh
node --test test/site.test.mjs test/motion.test.mjs
```

官网只部署静态文件，不构建或安装 Android 应用，也不需要发布签名密钥、环境变量或设备信息。不要上传 `.vercel`、`.env`、密钥或设备备份。
