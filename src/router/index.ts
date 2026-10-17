import { createRouter, createWebHistory } from 'vue-router'

import HomeView from '@/views/HomeView.vue'
import DocumentsView from '@/views/DocumentsView.vue'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import { isAuthenticated } from '@/services/auth'
import DocumentView from '@/views/DocumentView.vue'
import PublicDocumentsView from '@/views/PublicDocumentsView.vue'

const router = createRouter({
    history: createWebHistory(),
    routes: [
        {
            path: '/',
            component: HomeView,
        },
        {
            path: '/documents',
            component: DocumentsView,
            meta: { requiresAuth: true },
        },
        { path: '/login', component: LoginView, meta: { guestOnly: true } },
        { path: '/register', component: RegisterView, meta: { guestOnly: true } },
        { path: '/documents/:id', component: DocumentView },
        { path: '/public/documents', component: PublicDocumentsView  },
    ],
})

router.beforeEach((to) => {
    if (to.meta.requiresAuth && !isAuthenticated()) return '/login'
    if (to.meta.guestOnly && isAuthenticated()) return '/documents'
})

export default router