param([string]$BaseUrl = 'http://127.0.0.1:8080/api')
$ErrorActionPreference = 'Stop'
$base = $BaseUrl
function Login-Headers([string]$Username) {
    $body = @{ username=$Username; password='PetDemo123!' } | ConvertTo-Json
    $session = Invoke-RestMethod "$base/auth/login" -Method Post -ContentType 'application/json' -Body $body
    return @{ Authorization="Bearer $($session.accessToken)" }
}
$owner = Login-Headers 'owner'
$sitter = Login-Headers 'sitter'
$null = Invoke-RestMethod "$base/health"
$petBody = @{ name='演示宠物'; notes='喂粮并换水' } | ConvertTo-Json
$pet = Invoke-RestMethod "$base/pets" -Method Post -Headers $owner -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($petBody))
# 随机选择未来时间，避免多次演示创建的时段重叠。
$start = [DateTimeOffset]::Now.AddDays(30).AddMinutes((Get-Random -Minimum 1 -Maximum 100000))
$body = @{ start=$start.ToString('o'); end=$start.AddHours(1).ToString('o'); priceCents=5000 } | ConvertTo-Json
$slot = Invoke-RestMethod "$base/slots" -Method Post -Headers $sitter -ContentType 'application/json' -Body $body
$addresses = Invoke-RestMethod "$base/addresses" -Headers $owner
$address = $addresses | Where-Object REGION -eq $slot.REGION | Select-Object -First 1
if (!$address) {
    $addressBody = @{ contact='演示主人'; phone='13800000000'; region=$slot.REGION; detail='虚拟演示地址' } | ConvertTo-Json
    $address = Invoke-RestMethod "$base/addresses" -Method Post -Headers $owner -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($addressBody))
}
$booking = @{ petId=$pet.ID; slotId=$slot.ID; addressId=$address.ID } | ConvertTo-Json
$headers = @{ Authorization=$owner.Authorization; 'Idempotency-Key'=[guid]::NewGuid().ToString() }
$order = Invoke-RestMethod "$base/orders" -Method Post -Headers $headers -ContentType 'application/json' -Body $booking
$retry = Invoke-RestMethod "$base/orders" -Method Post -Headers $headers -ContentType 'application/json' -Body $booking
if ($retry.ID -ne $order.ID) { throw '幂等校验失败' }
$accepted = Invoke-RestMethod "$base/orders/$($order.ID)/accept" -Method Post -Headers $sitter
if ($accepted.STATUS -ne 'ACCEPTED') { throw '接单失败' }
$cancelled = Invoke-RestMethod "$base/orders/$($order.ID)/cancel" -Method Post -Headers $owner
if ($cancelled.STATUS -ne 'CANCELLED') { throw '取消失败' }
$available = Invoke-RestMethod "$base/slots"
if (!($available | Where-Object ID -eq $slot.ID)) { throw '时段释放失败' }
Invoke-RestMethod "$base/auth/logout" -Method Post -Headers $owner
Invoke-RestMethod "$base/auth/logout" -Method Post -Headers $sitter
Write-Output "HTTP 演示通过：创建宠物、发布时段、预约、幂等重试、接单、取消、释放时段。订单：$($order.ID)"
