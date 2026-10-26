import { chromium } from '@playwright/test'
import { resolve } from 'node:path'
const browser=await chromium.launch({channel:'chrome'})
const failures=[]
try {
 for(const [user,title] of [['owner','宠物照护助手'],['sitter','服务执行助手'],['admin','平台运营助手']]){
  const page=await browser.newPage({viewport:{width:1440,height:1100}})
  page.on('pageerror',e=>failures.push(e.message))
  await page.goto('http://127.0.0.1:5181/login')
  await page.getByLabel('用户名',{exact:true}).fill(user)
  await page.getByLabel('密码',{exact:true}).fill('PetDemo123!')
  await page.getByRole('button',{name:'登录并进入'}).click()
  await page.waitForURL('**/workspace')
  await page.getByRole('heading',{name:title,exact:true}).waitFor()
  await page.locator('.agent-overview strong').first().filter({hasText:/\d/}).waitFor()
  await page.locator('.agent-suggestions button').first().click()
  if(!(await page.locator('.chat-composer textarea').inputValue()))throw Error('快捷提问未填入')
  await page.screenshot({path:resolve('../docs/screenshots/agent-workspace-'+user+'.png'),fullPage:true})
  await page.setViewportSize({width:390,height:844})
  if(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+2))throw Error(user+'手机页面溢出')
  console.log(user+': login, role homepage, live counts, task button, mobile width passed')
  await page.close()
 }
 if(failures.length)throw Error(failures.join('\n'))
}finally{await browser.close()}
