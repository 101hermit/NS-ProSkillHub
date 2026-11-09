import http from './client';

/** 专业版接口封装：连通矩阵 / 实验矩阵 / 知识图谱 / 分析报告。 */

/* --------------------------- 连通矩阵 --------------------------- */
export const connMatrix = () => http.get('/conn/matrix');
export const connHeatmap = () => http.get('/conn/heatmap');

/* --------------------------- 实验矩阵 --------------------------- */
export const createMatrix = (body) => http.post('/eval/matrix', body);
export const listMatrices = () => http.get('/eval/matrix');
export const getMatrix = (code) => http.get(`/eval/matrix/${encodeURIComponent(code)}`);
export const runMatrix = (code) => http.post(`/eval/matrix/${encodeURIComponent(code)}/run`);
export const compareMatrix = (code) => http.get(`/eval/matrix/${encodeURIComponent(code)}/compare`);
export const deleteMatrix = (code) => http.del(`/eval/matrix/${encodeURIComponent(code)}`);

/**
 * 实验报告的下载地址（Markdown）。
 *
 * 为什么给「地址」而不是「请求一次再写文件」：这个端点返回的是文档流，
 * 让浏览器直接下载更稳（文件名、编码都由服务端定好），前端不必再造 Blob。
 */
export const matrixReportUrl = (code) =>
  `/api/v1/eval/matrix/${encodeURIComponent(code)}/report.md`;

/* --------------------------- 知识图谱 --------------------------- */
export const rebuildKg = () => http.post('/kg/rebuild');
export const kgGraph = (params) => http.get('/kg/graph', { params });
export const kgEntities = (params) => http.get('/kg/entities', { params });
export const kgRelations = (params) => http.get('/kg/relations', { params });
export const kgSimilar = (skillId, top = 5) =>
  http.get(`/kg/similar/${encodeURIComponent(skillId)}`, { params: { top } });
export const kgSnapshots = () => http.get('/kg/snapshots');
export const kgExportUrl = (format = 'json', scope = 'global') =>
  `/api/v1/kg/export?format=${format}&scope=${encodeURIComponent(scope)}`;

/* --------------------------- 分析 --------------------------- */
export const analyze = (body) => http.post('/ix/analyze', body);
export const listReports = (limit = 20) => http.get('/ix/reports', { params: { limit } });
export const getReport = (code) => http.get(`/ix/reports/${encodeURIComponent(code)}`);
