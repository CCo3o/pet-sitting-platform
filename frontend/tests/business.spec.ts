import { test,expect,type Page,type APIRequestContext } from '@playwright/test'
const base=process.env.E2E_BACKEND_URL??'http://127.0.0.1:8080'
async function capture(page:Page,path:string){
  await expect(page.locator('.el-overlay:visible')).toHaveCount(0)
  await expect(page.locator('.el-loading-mask:visible')).toHaveCount(0)
  await expect(page.locator('.el-message')).toHaveCount(0)
  await page.screenshot({path,fullPage:true,animations:'disabled'})
}
async function login(page:Page,username:string,password='PetDemo123!'){
  await page.goto('/login');await page.getByLabel('用户名',{exact:true}).fill(username);await page.getByLabel('密码',{exact:true}).fill(password);await page.getByRole('button',{name:'登录并进入'}).click();await expect(page).toHaveURL('/workspace')
}
async function token(request:APIRequestContext,username:string,password='PetDemo123!'){
  const response=await request.post(`${base}/api/auth/login`,{data:{username,password}});expect(response.ok()).toBeTruthy();return (await response.json()).accessToken as string
}
test('主人管理宠物与地址，预约、模拟付款、取消和查看订单',async({page,request})=>{
  const username=`owner_e2e_${Date.now()}`,password='E2ePass123!'
  const registered=await request.post(`${base}/api/auth/register`,{data:{username,password,displayName:'浏览器测试主人',role:'OWNER'}});expect(registered.status()).toBe(201)
  await login(page,username,password)
  await page.getByRole('link',{name:'宠物档案',exact:true}).click()
  await page.getByRole('button',{name:'添加宠物',exact:true}).click()
  await page.getByLabel('宠物名字',{exact:true}).fill('团子')
  await page.getByLabel('喂养习惯与注意事项',{exact:true}).fill('每日喂粮、换水，不要开窗。')
  await page.getByRole('button',{name:'保存',exact:true}).click()
  await expect(page.getByRole('heading',{name:'团子',exact:true})).toBeVisible()
  await capture(page,'../docs/screenshots/pet-profiles.png')
  await page.getByRole('link',{name:'上门地址',exact:true}).click();await page.getByRole('button',{name:'添加地址',exact:true}).click()
  await page.getByLabel('联系人',{exact:true}).fill('小张');await page.getByLabel('联系电话',{exact:true}).fill('13800000000');await page.getByLabel('服务区域',{exact:true}).fill('示范区');await page.getByLabel('详细地址',{exact:true}).fill('演示街道 2 号（虚拟地址）')
  await page.getByRole('button',{name:'保存',exact:true}).click();await expect(page.getByRole('heading',{name:'小张',exact:true})).toBeVisible()
  await capture(page,'../docs/screenshots/addresses.png')
  const sitter=await token(request,'sitter'),start=new Date(Date.now()+86400000+Math.floor(Math.random()*1000000000)),end=new Date(start.getTime()+3600000)
  const published=await request.post(`${base}/api/slots`,{headers:{Authorization:`Bearer ${sitter}`},data:{start:start.toISOString(),end:end.toISOString(),priceCents:5000}});expect(published.ok()).toBeTruthy()
  const slot=await published.json()
  await page.getByRole('link',{name:'预约服务',exact:true}).click()
  await expect(page.getByRole('heading',{name:'预约上门服务'})).toBeVisible()
  await capture(page,'../docs/screenshots/booking.png')
  const card=page.locator('.profile-card').filter({hasText:new Date(slot.STARTS_AT).toLocaleString('zh-CN',{hour12:false})})
  // 使用页面时间文本定位本次发布时段，避免误选其他用户的演示数据。
  await card.getByRole('button',{name:'选择并预约'}).click()
  const dialog=page.getByRole('dialog')
  await dialog.getByText('选择本次照顾的宠物',{exact:true}).click();await page.getByRole('option',{name:'团子',exact:true}).click()
  await dialog.getByText('选择本次上门地址',{exact:true}).click();await page.getByRole('option',{name:/小张 · 示范区/}).click()
  await dialog.getByRole('button',{name:'确认创建预约'}).click();await expect(page).toHaveURL('/orders')
  await expect(page.locator('.order-card')).toHaveCount(1)
  await capture(page,'../docs/screenshots/orders.png')
  await page.getByRole('button',{name:'模拟支付',exact:true}).click();await page.getByRole('button',{name:'确定',exact:true}).click();await expect(page.getByText('订单已更新',{exact:true})).toBeVisible()
  await page.getByRole('button',{name:'取消预约',exact:true}).click();await page.getByRole('button',{name:'确定',exact:true}).click();await expect(page.locator('.order-card .el-tag')).toHaveText('已取消')
  await page.getByRole('button',{name:'详情与喂养记录'}).click();await expect(page.getByText(/已模拟退款/)).toBeVisible()
})
test('喂养员提交资料、管理员审核、喂养员发布时段',async({page,request})=>{
  const username=`sitter_e2e_${Date.now()}`,password='E2ePass123!',displayName=`测试照顾员${Date.now().toString().slice(-6)}`
  expect((await request.post(`${base}/api/auth/register`,{data:{username,password,displayName,role:'SITTER'}})).status()).toBe(201)
  await login(page,username,password);await page.getByRole('link',{name:'服务资料与时段'}).click()
  await page.getByLabel('服务介绍',{exact:true}).fill('有照顾猫狗经验，提供喂粮、换水与照片记录。');await page.getByRole('button',{name:'提交资料审核'}).click()
  await expect(page.getByText('等待审核',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'发布时段',exact:true})).toBeDisabled()
  await page.getByRole('button',{name:'退出登录'}).click();await login(page,'admin')
  await page.getByRole('link',{name:'平台管理',exact:true}).click();const row=page.getByRole('row').filter({hasText:displayName}).first()
  await row.getByRole('button',{name:'通过',exact:true}).click();await page.getByRole('button',{name:'确定',exact:true}).click();await expect(row.getByText('已通过',{exact:true})).toBeVisible()
  await capture(page,'../docs/screenshots/admin-platform.png')
  await page.getByRole('button',{name:'退出登录'}).click();await login(page,username,password);await page.getByRole('link',{name:'服务资料与时段'}).click();await expect(page.getByRole('button',{name:'发布时段',exact:true})).toBeEnabled()
  const start=new Date(Date.now()+86400000+Math.floor(Math.random()*1000000000)),end=new Date(start.getTime()+3600000)
  const inputDate=(d:Date)=>`${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}:00`
  await page.getByPlaceholder('选择开始时间').fill(inputDate(start));await page.getByPlaceholder('选择开始时间').press('Enter')
  await page.getByPlaceholder('选择结束时间').fill(inputDate(end));await page.getByPlaceholder('选择结束时间').press('Enter')
  await page.getByRole('button',{name:'发布时段',exact:true}).click();await expect(page.getByText('时段已发布',{exact:true})).toBeVisible()
  await capture(page,'../docs/screenshots/sitter-profile.png')
})
test('未配置助手如实提示，角色页面不能越权访问',async({page})=>{
  await login(page,'owner');await page.getByRole('link',{name:'预约助手',exact:true}).click();await expect(page.getByText('DeepSeek 尚未配置',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'发送需求'})).toBeDisabled()
  await capture(page,'../docs/screenshots/agent.png')
  await page.goto('/admin');await expect(page).toHaveURL('/workspace')
  await page.setViewportSize({width:390,height:844});await page.goto('/pets');expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy();await capture(page,'../docs/screenshots/pets-mobile.png')
})
test('主人提交投诉，管理员处理后主人看到结果',async({page,request})=>{
  const username=`complaint_${Date.now()}`,password='E2ePass123!'
  const ownerReply=await request.post(`${base}/api/auth/register`,{data:{username,password,displayName:'投诉流程测试主人',role:'OWNER'}})
  const owner=(await ownerReply.json()).accessToken as string,headers={Authorization:`Bearer ${owner}`}
  const pet=await (await request.post(`${base}/api/pet-profiles`,{headers,data:{name:'豆豆',notes:'换水',species:'CAT',breed:'',ageMonths:12}})).json()
  const address=await (await request.post(`${base}/api/addresses`,{headers,data:{contact:'测试主人',phone:'13800000000',region:'示范区',detail:'虚拟测试门牌'}})).json()
  const sitter=await token(request,'sitter'),start=new Date(Date.now()+86400000+Math.floor(Math.random()*2000000000)),end=new Date(start.getTime()+3600000)
  const slot=await (await request.post(`${base}/api/slots`,{headers:{Authorization:`Bearer ${sitter}`},data:{start:start.toISOString(),end:end.toISOString(),priceCents:5000}})).json()
  const orderReply=await request.post(`${base}/api/orders`,{headers:{...headers,'Idempotency-Key':`complaint-${Date.now()}`},data:{petId:pet.ID,addressId:address.ID,slotId:slot.ID}})
  expect(orderReply.ok()).toBeTruthy();const order=await orderReply.json()
  await login(page,username,password);await page.getByRole('link',{name:'服务订单',exact:true}).click();await page.getByRole('button',{name:'投诉与反馈',exact:true}).click()
  await page.getByRole('dialog').getByRole('textbox').fill(`请核实本次预约安排 ${order.ID}`);await page.getByRole('button',{name:'确定',exact:true}).click();await expect(page.getByText(/投诉已提交/)).toBeVisible()
  await page.getByRole('button',{name:'退出登录'}).click();await login(page,'admin');await page.getByRole('link',{name:'投诉与反馈',exact:true}).click()
  const card=page.locator('.record-card').filter({hasText:order.ID});await card.getByRole('button',{name:'填写处理结果'}).click();await page.getByRole('dialog').getByRole('textbox').fill('已联系双方核实，预约安排已确认。');await page.getByRole('button',{name:'确定',exact:true}).click();await expect(card.getByText('已处理',{exact:true})).toBeVisible()
  await capture(page,'../docs/screenshots/complaints.png')
  await page.getByRole('button',{name:'退出登录'}).click();await login(page,username,password);await page.getByRole('link',{name:'投诉与反馈',exact:true}).click();await expect(page.getByText(/预约安排已确认/)).toBeVisible()
})
test('管理员停用账户会撤销登录，随后可重新启用',async({page,request})=>{
  const username=`disabled_${Date.now()}`,password='E2ePass123!'
  const registered=await request.post(`${base}/api/auth/register`,{data:{username,password,displayName:'账户状态测试',role:'OWNER'}}),owner=await registered.json()
  await login(page,'admin');await page.getByRole('link',{name:'平台管理',exact:true}).click()
  const row=page.getByRole('row').filter({hasText:username});await row.getByRole('button',{name:'停用',exact:true}).click();await page.getByRole('button',{name:'确定',exact:true}).click();await expect(row.getByText('已停用',{exact:true})).toBeVisible()
  expect((await request.get(`${base}/api/auth/me`,{headers:{Authorization:`Bearer ${owner.accessToken}`}})).status()).toBe(401)
  await row.getByRole('button',{name:'启用',exact:true}).click();await page.getByRole('button',{name:'确定',exact:true}).click();await expect(row.getByText('可用',{exact:true})).toBeVisible()
})
