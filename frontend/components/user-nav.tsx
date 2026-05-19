"use client";

import { useAuthGuard } from "@/lib/auth/use-auth";
import { ActionIcon, Avatar, Button } from "@mantine/core";
import Link from "next/link";

export function UserNav() {
  const { user, logout } = useAuthGuard({ middleware: "auth" });

  return (
    <div className="flex items-center gap-2">
      <Link href="/profile" title={user?.email}>
        <ActionIcon variant="subtle" radius={100}>
          <Avatar name={user?.firstName}></Avatar>
        </ActionIcon>
      </Link>
      <Button variant="subtle" size="compact-sm" onClick={() => logout()}>Log out</Button>
    </div>
  );
}
