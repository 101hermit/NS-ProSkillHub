import http from './client';

/** 实测与接入接口封装。 */
export const runEval = (body) => http.post('/eval/runs', body);
export const listRuns = (params) => http.get('/eval/runs', { params });
export const getRun = (code) => http.get(`/eval/runs/${encodeURIComponent(code)}`);
export const getMetrics = () => http.get('/eval/metrics');
export const getSummary = (skillId) => http.get('/eval/summary', { params: { skill_id: skillId } });
export const getRanking = () => http.get('/eval/ranking');

export const listAgents = () => http.get('/agents');
export const saveAgent = (code, body) =>
  code ? http.put(`/agents/${encodeURIComponent(code)}`, body) : http.post('/agents', body);
export const deleteAgent = (code) => http.del(`/agents/${encodeURIComponent(code)}`);
export const probeAgent = (code) => http.post(`/agents/${encodeURIComponent(code)}/probe`);

export const listMcps = () => http.get('/mcp');
export const saveMcp = (code, body) =>
  code ? http.put(`/mcp/${encodeURIComponent(code)}`, body) : http.post('/mcp', body);
export const deleteMcp = (code) => http.del(`/mcp/${encodeURIComponent(code)}`);
export const probeMcp = (code) => http.post(`/mcp/${encodeURIComponent(code)}/probe`);

export const doctor = () => http.get('/conn/doctor');
