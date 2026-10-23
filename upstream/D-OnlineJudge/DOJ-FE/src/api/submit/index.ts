// 统一管理项目接口
import request from '@/utils/request';
import type { submitForm, executeResponseData, problemSubmitForm, sidResponseData } from './type';
import type { BaseResponseData } from '@/api/base';
import { AxiosResponse } from 'axios';

enum API {
    SUBMIT_URL = '/sandbox/code',
    PROBLEMS_SUBMIT_URL = '/sandbox/problem',
    PROBLEMS_VALIDATE_URL = '/sandbox/validate'
};

// 暴露请求函数
export const reqSubmit = (data: FormData) => request.post< executeResponseData, AxiosResponse<executeResponseData>>(API.SUBMIT_URL, data);
export const reqProblemSubmit = (data: FormData) => request.post< executeResponseData, AxiosResponse<executeResponseData>>(API.PROBLEMS_SUBMIT_URL, data);
export type IdResponse = { code: number; msg: string; data: number };
export const reqProblemValidate = (data: FormData) => request.post<IdResponse, AxiosResponse<IdResponse>>(API.PROBLEMS_VALIDATE_URL, data);
export const reqAttemptValidate = (attemptId: string, data: FormData) => request.post<IdResponse, AxiosResponse<IdResponse>>(`/sandbox/attempts/${attemptId}/validate`, data);
export const reqStartAttempt = (assessmentId: string) => request.post<IdResponse, AxiosResponse<IdResponse>>(`/assessments/${assessmentId}/attempts`);
export const reqAttemptProblem = (attemptId: string) => request.get<IdResponse, AxiosResponse<IdResponse>>(`/assessments/attempts/${attemptId}/problem-id`);
