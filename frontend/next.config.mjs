/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  transpilePackages: [
    '@mantine/core',
    '@mantine/hooks',
    '@mantine/form',
    '@mantine/dates',
    '@mantine/notifications',
    '@mantine/modals',
    '@mantine/nprogress',
    '@mantine/spotlight',
    '@mantine/carousel',
    '@mantine/charts',
    '@mantine/dropzone',
    '@mantine/tiptap',
    '@mantine/code-highlight',
  ],
};

export default nextConfig;
