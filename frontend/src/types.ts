export type Role = 'OWNER' | 'SITTER' | 'ADMIN'
export interface User {
  id: number
  username: string
  displayName: string
  role: Role
  enabled: boolean
}
export interface AuthResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresAt: string
  user: User
}
export const roleNames: Record<Role, string> = {
  OWNER: '宠物主人', SITTER: '喂养员', ADMIN: '管理员',
}
