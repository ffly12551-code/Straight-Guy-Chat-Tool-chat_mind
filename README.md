# Straight-Guy-Chat-Tool - AI聊天分析与回复助手

一款基于 Flutter 开发的 Android App，通过 AI 大模型+知识库，分析聊天截图内容，帮助用户读懂对方心理、对方意图、识别沟通风险、生成高质量回复建议。

## 功能特点

- 📷 **截图AI分析**：一键截图，AI自动分析对方心理状态、真实意图、潜在风险
- 💬 **智能回复建议**：根据分析结果，给出2-3条符合价值观的回复建议
- 🪟 **悬浮窗模式**：边聊边看，无需切换APP，在微信/QQ等任何APP上方直接分析
- 🌍 **多场景支持**：职场、亲密关系、家庭、社交四大场景切换
- ⚖️ **价值观约束**：自定义处事底线和各场景沟通原则，让AI回复更贴合你的风格

## 技术栈

- **框架**: Flutter 3.12+
- **语言**: Dart / Kotlin
- **AI模型**: 视觉大模型 （后续建议自己训练知识库模型，这里为了验证功能）
- **原生能力**: Android 悬浮窗、MediaProjection 截图

## 快速开始

### 环境要求

- Flutter 3.12+
- Android SDK 26+ (Android 8.0+)

### 安装步骤

1. 克隆仓库

```bash
git clone https://github.com/ffly12551-code/Straight-Guy-Chat-Tool-chat_mind.git
cd Straight-Guy-Chat-Tool-chat_mind
```

2. 安装依赖

```bash
flutter pub get
```

3. 配置 API Key

- 前往 [火山引擎控制台](https://console.volcengine.com/ark/) 获取 API Key
- 在APP设置页面配置 API Key

4. 运行

```bash
flutter run
```

## 使用说明

### 方式一：APP内分析

1. 打开 APP
2. 选择场景（职场/亲密关系/家庭/社交）
3. 点击「从相册选择」或截图
4. 查看AI分析结果

### 方式二：悬浮窗模式（推荐）

1. 打开 APP
2. 点击「打开悬浮窗」，授予权限
3. 返回微信/QQ等聊天APP
4. 点击悬浮球 → 框选聊天区域 → 查看分析结果

## 项目结构

```
Straight-Guy-Chat-Tool-chat_mind/
├── lib/
│   ├── models/          # 数据模型
│   ├── pages/           # 页面组件
│   ├── services/        # 服务层（AI、截图、悬浮窗等）
│   └── utils/           # 工具类
├── android/             # Android 原生代码
├── pubspec.yaml         # 依赖配置
└── README.md            # 项目说明
```

## 核心文件

| 文件 | 作用 |
|------|------|
| `lib/services/ai_service.dart` | AI分析服务，封装豆包API调用 |
| `lib/services/screenshot_service.dart` | 截图服务，处理截图数据流 |
| `lib/services/float_window_service.dart` | 悬浮窗服务，Flutter端封装 |
| `lib/pages/home_page.dart` | 主页面，场景切换与分析入口 |
| `android/app/src/main/kotlin/.../FloatWindowService.kt` | 悬浮窗服务（核心） |

## 开源协议

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！

## 致谢

- [火山引擎豆包大模型](https://www.doubao.com/)
- [Flutter](https://flutter.dev/)
