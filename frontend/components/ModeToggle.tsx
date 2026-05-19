"use client"
 
import * as React from "react"
import { Moon, Sun } from "lucide-react"
import { useTheme } from "next-themes"
import { ActionIcon, Button } from "@mantine/core"
 
export default function ModeToggle() {
  const { setTheme } = useTheme()
 
  return (
    <div className="flex items-center gap-1">
      <ActionIcon variant="transparent" radius={100} size="icon" onClick={() => setTheme('system')}>
        <Sun className="h-[1.2rem] w-[1.2rem] rotate-0 scale-100 transition-all dark:-rotate-90 dark:scale-0" />
        <Moon className="absolute h-[1.2rem] w-[1.2rem] rotate-90 scale-0 transition-all dark:rotate-0 dark:scale-100" />
        <span className="sr-only">Toggle theme</span>
      </ActionIcon>
      <Button variant="subtle" size="compact-xs" onClick={() => setTheme('light')}>Light</Button>
      <Button variant="subtle" size="compact-xs" onClick={() => setTheme('dark')}>Dark</Button>
    </div>
  )
}
