import { chromium } from '@playwright/test'
import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const reportPath=process.argv[2]
if(!reportPath)throw new Error('请传入真实评测JSON报告路径')
const report=JSON.parse(await readFile(reportPath,'utf8'))
if(report.mode!=='real-backend')throw new Error('只接受真实后端评测报告')
const result=[...report.results].reverse().find(r=>r.passed&&r.turns.some(t=>t.draft))
if(!result)throw new Error('报告中没有成功的真实草稿调用')
const trace=[...result.turns].reverse().find(t=>t.draft).trace
const base=process.env.DEMO_BASE_URL??'http://127.0.0.1:5173'
const browser=await chromium.launch({channel:process.env.PLAYWRIGHT_CHANNEL??'chrome'})
const page=await browser.newPage({viewport:{width:1440,height:1000}})
const errors=[]
page.on('pageerror',error=>errors.push(error.message))
try{
  await page.goto(base+'/login')
  await page.getByLabel('用户名',{exact:true}).fill('admin')
  await page.getByLabel('密码',{exact:true}).fill('PetDemo123!')
  await page.getByRole('button',{name:'登录并进入'}).click()
  await page.waitForURL('**/workspace')
  await page.getByRole('link',{name:'平台管理',exact:true}).click()
  const section=page.locator('section').filter({has:page.getByRole('heading',{name:'最近预约助手调用',exact:true})})
  const card=section.locator(':scope > article').filter({hasText:`用户 ${trace.OWNER_ID} · DRAFTED`})
  await card.getByRole('button',{name:'查看调用详情',exact:true}).click()
  await card.getByText(`调用编号：${trace.ID}`,{exact:false}).waitFor()
  await card.locator('details').evaluateAll(elements=>elements.forEach(element=>element.open=true))
  if(errors.length)throw new Error(errors.join('\n'))
  await card.screenshot({path:resolve('../docs/screenshots/agent-trace-real.png'),animations:'disabled'})
  console.log('已保存真实工具追踪截图：'+trace.ID)
  await page.getByRole('button',{name:'退出登录',exact:true}).click()
}finally{await browser.close()}
