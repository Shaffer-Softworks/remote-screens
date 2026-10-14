import { createApp } from 'vue'
import App from './App.vue'
import { vRange } from './range'
import './style.css'

createApp(App).directive('range', vRange).mount('#app')
