import { createApp } from 'vue';
import router from './router';
import App from './App.vue';
import './styles/base.css';

const app = createApp(App);
app.use(router);

// 全局兜底：任何未捕获的渲染错误都进控制台，但页面不白屏
app.config.errorHandler = (err, instance, info) => {
  console.error('[vue]', info, err);
};

app.mount('#app');
