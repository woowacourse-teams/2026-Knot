import { http, HttpResponse } from "msw";

import { WORKSPACES_API_PATH } from "@api/fetch/api/v1/workspaces";
import {
  workspaceCreateResponse,
  workspacesResponse,
} from "@api/mock/responses/workspace";

export const workspacesHandlers = [
  http.get(`*${WORKSPACES_API_PATH}`, () =>
    HttpResponse.json(workspacesResponse),
  ),
  http.post(`*${WORKSPACES_API_PATH}`, () =>
    HttpResponse.json(workspaceCreateResponse, { status: 201 }),
  ),
];
