export class ApiError extends Error {
  constructor(public readonly status: number, message: string) { super(message) }
}

export async function request<T>(path: string, options: RequestInit = {}, token?: string): Promise<T> {
  const headers = new Headers(options.headers)
  if (options.body) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  let response: Response
  try { response = await fetch(`/api${path}`, { ...options, headers }) }
  catch { throw new ApiError(0, '暂时无法连接服务，请确认后端已启动后重试。') }
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new ApiError(response.status, body?.message ?? '请求失败，请稍后重试。')
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
