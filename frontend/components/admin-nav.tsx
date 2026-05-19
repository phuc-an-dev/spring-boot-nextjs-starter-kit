"use client";

import React from "react";
import Link from "next/link";
import RoleGuard from "./role-guard";
import { Role } from "@/models/user/UserResponse";
import { Button } from "@mantine/core";

export default function AdminNav() {
  return (
    <RoleGuard rolesAllowed={[Role.ADMIN]}>
      <div className="flex items-center gap-1">
        <Link href="/admin/users">
          <Button variant="subtle" size="compact-sm">Users</Button>
        </Link>
        <Link href="/admin/notifications">
          <Button variant="subtle" size="compact-sm">Notifications</Button>
        </Link>
      </div>
    </RoleGuard>
  );
}
