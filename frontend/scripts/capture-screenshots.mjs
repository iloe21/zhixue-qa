/**
 * README 界面截图脚本（开发工具，非交付物）
 *
 * 用法（需本地起好前后端，且已用 prepare-demo-data 流程备好演示数据）：
 *   node scripts/capture-screenshots.mjs
 *
 * 复用本机 Edge 无头模式截图，输出到 docs/screenshots/。
 * 登录态通过 localStorage 注入 token 实现，不模拟表单输入。
 */
import { readFileSync, mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import puppeteer from 'puppeteer-core'

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..')
const outDir = join(root, 'docs', 'screenshots')
mkdirSync(outDir, { recursive: true })

const EDGE = 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'
const BASE = 'http://localhost:5173'

const demoToken = readFileSync(join(root, 'backend/target/demo-token.txt'), 'utf8').trim()
const adminToken = readFileSync(join(root, 'backend/target/admin-token.txt'), 'utf8').trim()

const demoUser = { id: 5, username: 'demo_user', nickname: '演示同学', role: 'student' }
const adminUser = { id: 1, username: 'admin', nickname: '系统管理员', role: 'admin' }

const sleep = ms => new Promise(r => setTimeout(r, ms))

async function shoot(page, name, { token, user, path, waitSel, extraMs = 1200 }) {
  await page.evaluateOnNewDocument((t, u) => {
    localStorage.clear()
    if (t) {
      localStorage.setItem('token', t)
      localStorage.setItem('user', JSON.stringify(u))
    }
  }, token || null, user || null)
  await page.goto(BASE + path, { waitUntil: 'networkidle0', timeout: 60000 })
  if (waitSel) await page.waitForSelector(waitSel, { timeout: 20000 })
  await sleep(extraMs)
  await page.screenshot({ path: join(outDir, name) })
  console.log('captured', name)
}

const browser = await puppeteer.launch({
  executablePath: EDGE,
  headless: true,
  defaultViewport: { width: 1440, height: 900 }
})
const page = await browser.newPage()

// 1 登录页（无登录态，含图形验证码）
await shoot(page, '01-login.png', { path: '/login', waitSel: 'img[src^="data:image"]' })
// 2 首页
await shoot(page, '02-home.png', { token: demoToken, user: demoUser, path: '/home' })
// 3 知识库列表
await shoot(page, '03-kb-list.png', { token: demoToken, user: demoUser, path: '/kbs' })
// 4 知识库详情（文档解析状态）
await shoot(page, '04-kb-detail.png', { token: demoToken, user: demoUser, path: '/kbs/4', waitSel: '.el-table__row' })
// 5 问答页（Markdown 回答 + 引用卡片）
await shoot(page, '05-chat.png', { token: demoToken, user: demoUser, path: '/chat?conv=46', waitSel: '.cit-tag', extraMs: 1500 })
// 6 练习列表（判分结果）
await shoot(page, '06-exercise-list.png', { token: demoToken, user: demoUser, path: '/exercises', waitSel: '.el-table__row, .el-card' })
// 7 练习详情（得分与解析）
await shoot(page, '07-exercise-result.png', { token: demoToken, user: demoUser, path: '/exercises/5', extraMs: 1500 })
// 8 管理后台仪表盘
await shoot(page, '08-admin-dashboard.png', { token: adminToken, user: adminUser, path: '/admin', extraMs: 1800 })
// 9 用户管理
await shoot(page, '09-admin-users.png', { token: adminToken, user: adminUser, path: '/admin/users', waitSel: '.el-table__row' })

await browser.close()
console.log('done ->', outDir)
