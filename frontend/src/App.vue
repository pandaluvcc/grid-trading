<template>
  <div id="app">
    <router-view v-slot="{ Component, route }">
      <transition :name="route.meta.transition || 'page-fade'" mode="out-in">
        <component :is="Component" :key="route.path" />
      </transition>
    </router-view>
  </div>
</template>

<script setup></script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family:
    -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', 'Oxygen', 'Ubuntu', 'Cantarell', 'Fira Sans', 'Droid Sans',
    'Helvetica Neue', sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

#app {
  min-height: 100vh;
  background-color: var(--bg-color, #f5f5f5);
  transition: background-color var(--transition-base, 0.2s ease);
}

/* 页面过渡动画 - 淡入淡出 */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity var(--page-transition-duration, 0.25s) ease;
}

.page-fade-enter-from,
.page-fade-leave-to {
  opacity: 0;
}

/* 页面过渡动画 - 滑动 */
.page-slide-enter-active,
.page-slide-leave-active {
  transition: transform var(--page-transition-duration, 0.25s) ease, opacity var(--page-transition-duration, 0.25s) ease;
}

.page-slide-enter-from {
  transform: translateX(20px);
  opacity: 0;
}

.page-slide-leave-to {
  transform: translateX(-20px);
  opacity: 0;
}

/* 页面过渡动画 - 缩放 */
.page-scale-enter-active,
.page-scale-leave-active {
  transition: transform var(--page-transition-duration, 0.25s) ease, opacity var(--page-transition-duration, 0.25s) ease;
}

.page-scale-enter-from {
  transform: scale(0.95);
  opacity: 0;
}

.page-scale-leave-to {
  transform: scale(1.02);
  opacity: 0;
}
</style>
