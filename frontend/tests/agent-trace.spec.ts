import { test,expect } from '@playwright/test'

// UI fixture test only: backend trace authorization and execution are covered by Java integration tests.
test('管理员工具追踪界面展示查询参数、结果和失败类型（界面固定数据）',async({page})=>{
  const runs=[{ID:'ui-success',OWNER_ID:1001,CREATED_AT:'2026-10-12T19:00:00+08:00',OUTCOME:'DRAFTED'},
    {ID:'ui-failed',OWNER_ID:1002,CREATED_AT:'2026-10-12T19:01:00+08:00',OUTCOME:'FAILED'}]
  await page.route('**/api/admin/agent-runs',route=>route.fulfill({json:runs}))
  await page.route('**/api/agent/runs/ui-*',route=>{
    const failed=route.request().url().endsWith('ui-failed')
    return route.fulfill({json:{ID:failed?'ui-failed':'ui-success',OUTCOME:failed?'FAILED':'DRAFTED',DURATION_MS:123,
      ERROR_CODE:failed?'PROVIDER_OR_PROTOCOL_ERROR':'',TOOLS:[{id:'tool-one',sequence:1,toolName:'searchSlots',
        argumentsJson:'{"date":"2026-10-13","maxPriceCents":5000}',
        resultJson:failed?'{}':'[{"ID":"slot-example","PRICE_CENTS":5000}]',outcome:failed?'FAILED':'SUCCEEDED',
        errorCode:failed?'EXECUTION_ERROR':'',durationMs:12}]}})
  })
  await page.goto('/login');await page.getByLabel('用户名',{exact:true}).fill('admin');await page.getByLabel('密码',{exact:true}).fill('PetDemo123!');await page.getByRole('button',{name:'登录并进入'}).click()
  await expect(page).toHaveURL('/workspace');await page.getByRole('link',{name:'平台管理',exact:true}).click()
  const section=page.locator('section').filter({has:page.getByRole('heading',{name:'最近预约助手调用',exact:true})})
  await section.getByRole('button',{name:'查看调用详情',exact:true}).first().click()
  await expect(section.getByText('1. searchSlots',{exact:true})).toBeVisible()
  await section.getByText('查询参数',{exact:true}).click();await expect(section.locator('pre').first()).toContainText('5000')
  await section.getByText('结果摘要',{exact:true}).click();await expect(section.locator('pre').last()).toContainText('slot-example')
  await section.getByRole('button',{name:'查看调用详情',exact:true}).click()
  await expect(section.getByText('失败类型：EXECUTION_ERROR',{exact:true})).toBeVisible()
  await page.screenshot({path:'../docs/screenshots/agent-trace-ui-fixture.png',fullPage:true,animations:'disabled'})
})
