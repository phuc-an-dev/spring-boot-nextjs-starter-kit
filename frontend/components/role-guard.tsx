"use client"

import { useAuthGuard } from '@/lib/auth/use-auth'
import { Role } from '@/models/user/UserResponse'
import React from 'react'

interface RoleGuardProps {
  rolesAllowed?: Role[],
  children: React.ReactNode
}
export default function RoleGuard({rolesAllowed, children}: RoleGuardProps) {
  const {user} = useAuthGuard({middleware: 'guest'})
  if (!rolesAllowed) return null

  const isAllowed = rolesAllowed.includes(user?.role as Role)
  if (isAllowed) return children
}
