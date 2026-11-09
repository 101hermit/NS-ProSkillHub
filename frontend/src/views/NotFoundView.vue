<template>
  <div class="card not-found">
    <div class="code mono">404</div>
    <h1>这里没有页面</h1>
    <p class="muted small">
      地址可能拼错了，或者这个页面不在本版本里。三个版本的菜单不一样，但首页与 Skill 库永远在。
    </p>
    <div class="row">
      <RouterLink class="btn primary" to="/">回首页</RouterLink>
      <RouterLink class="btn" to="/skills">去 Skill 库</RouterLink>
      <RouterLink class="btn" to="/about">看本版本能力</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  padding: var(--sk-s10);
  text-align: center;
  max-width: 560px;
  margin: var(--sk-s10) auto;
}
.code {
  font-size: 64px;
  font-weight: 800;
  color: var(--sk-primary-weak);
  line-height: 1;
  letter-spacing: -2px;
}
.not-found .row {
  justify-content: center;
}
</style>
