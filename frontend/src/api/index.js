/**
 * 站点与 Skill 的全部接口封装（资源站只有一个后端域，因此集中在一个文件）。
 * 约定：函数返回 Promise<data>，失败抛 Error(msg)。
 */
import http from './client';

/* ------------------------------- 站点 ------------------------------- */
export const getHealth = () => http.get('/health');
export const getMeta = () => http.get('/meta');
export const getCapabilities = () => http.get('/meta/capabilities');
export const getCategories = () => http.get('/categories');
export const getStats = () => http.get('/index/stats');
export const search = (kw, limit = 12) => http.get('/index/search', { params: { kw, limit } });
export const getOpLog = (params) => http.get('/op-log', { params });

/* ------------------------------- Skill ------------------------------ */
export const listSkills = (params) => http.get('/skills', { params });
export const getSkill = (id) => http.get('/skills/by-id', { params: { id } });
export const getVersions = (id) => http.get(`/skills/${encodeURIComponent(id)}/versions`);
export const getVersionContent = (id, version) =>
  http.get(`/skills/${encodeURIComponent(id)}/versions/${encodeURIComponent(version)}`);
export const getAudit = (id) => http.get(`/skills/${encodeURIComponent(id)}/audit`);
export const getInstall = (id) => http.get(`/skills/${encodeURIComponent(id)}/install`);
export const editSkill = (id, body) => http.put(`/skills/${encodeURIComponent(id)}`, body);
export const archiveSkill = (id, hard = false) =>
  http.del(`/skills/${encodeURIComponent(id)}`, { params: { hard } });
export const restoreSkill = (id) => http.post(`/skills/${encodeURIComponent(id)}/restore`);
export const recordInstall = (id) => http.post(`/skills/${encodeURIComponent(id)}/install-record`);

/** 下载地址（直接给浏览器用的绝对路径，不经过 fetch 包装）。 */
export const downloadUrl = (id, version) => {
  const q = new URLSearchParams({ id });
  if (version) q.set('version', version);
  return `/api/v1/skills/download?${q.toString()}`;
};

/* ------------------------------- 贡献者 ----------------------------- */
export const listContributors = (params) => http.get('/contributors', { params });
export const getContributor = (source, author) =>
  http.get(`/contributors/${encodeURIComponent(source)}/${encodeURIComponent(author)}`);

/* ------------------------------- 贡献流程 ---------------------------- */
export const createContribution = (formData) => http.upload('/contributions', formData);
export const signCla = (id, signature) => http.post(`/contributions/${id}/cla`, { signature });
export const submitContribution = (id) => http.post(`/contributions/${id}/submit`);
export const publishContribution = (id) => http.post(`/contributions/${id}/publish`);
export const rejectContribution = (id, reason) =>
  http.post(`/contributions/${id}/reject`, null, { params: { reason } });
export const listContributions = (params) => http.get('/contributions', { params });
export const getContribution = (id) => http.get(`/contributions/${id}`);

/* ------------------------------- 风险 ------------------------------- */
export const getTaxonomy = () => http.get('/risk/taxonomy');
export const scanText = (content, fileName = 'SKILL.md') => http.post('/risk/scan', { content, fileName });

/* ------------------------------- 文档 ------------------------------- */
export const listDocs = () => http.get('/docs');
export const getDoc = (key) => http.get(`/docs/${encodeURIComponent(key)}`);
