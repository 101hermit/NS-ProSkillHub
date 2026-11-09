import http from './client';

/** 登录认证接口（三系统一致）。 */
export const register = (body) => http.post('/auth/register', body);
export const login = (body) => http.post('/auth/login', body);
export const guest = () => http.post('/auth/guest', null);
export const logout = () => http.post('/auth/logout', null);
export const me = () => http.get('/auth/me');
export const changePassword = (body) => http.post('/auth/password', body);
