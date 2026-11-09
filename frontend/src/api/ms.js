import http from './client';

/** Skill-MS（库语义）接口封装。 */
export const getSchema = () => http.get('/ms/schema');
export const getConcepts = () => http.get('/ms/concepts');
export const getTables = () => http.get('/ms/tables');

/** 对象树（库 → 索引 / 约束 / 事件），供对象浏览页的左侧导航。 */
export const getObjects = () => http.get('/ms/objects');

/** 单个对象的 DDL 卡。用查询参数而非路径变量：对象编号里可能出现 '/'。 */
export const getDdl = (type, code) => http.get('/ms/ddl', { params: { type, code } });

export const listSkillsets = () => http.get('/ms/skillsets');
export const createSkillset = (body) => http.post('/ms/skillsets', body);
export const deleteSkillset = (code) => http.del(`/ms/skillsets/${encodeURIComponent(code)}`);

export const listIndexes = () => http.get('/ms/indexes');
export const createIndex = (body) => http.post('/ms/indexes', body);
export const rebuildIndex = (code) => http.post(`/ms/indexes/${encodeURIComponent(code)}/rebuild`);
export const indexEntries = (code, limit = 20) =>
  http.get(`/ms/indexes/${encodeURIComponent(code)}/entries`, { params: { limit } });
export const dropIndex = (code) => http.del(`/ms/indexes/${encodeURIComponent(code)}`);

export const listConstraints = () => http.get('/ms/constraints');
export const saveConstraint = (code, body) =>
  code ? http.put(`/ms/constraints/${encodeURIComponent(code)}`, body) : http.post('/ms/constraints', body);
export const deleteConstraint = (code) => http.del(`/ms/constraints/${encodeURIComponent(code)}`);
export const validateConstraints = () => http.post('/ms/constraints/validate', {});

export const listEvents = () => http.get('/ms/events');
export const saveEvent = (code, body) =>
  code ? http.put(`/ms/events/${encodeURIComponent(code)}`, body) : http.post('/ms/events', body);
export const deleteEvent = (code) => http.del(`/ms/events/${encodeURIComponent(code)}`);
export const listEventLogs = (params) => http.get('/ms/events/logs', { params });
export const retryEventLog = (id) => http.post(`/ms/events/logs/${id}/retry`);

export const listTxLogs = (params) => http.get('/ms/tx-log', { params });

export const runQuery = (sql) => http.post('/ms/query', { sql });
export const explainQuery = (sql) => http.post('/ms/query/plan', { sql });
