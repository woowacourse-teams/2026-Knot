import axios from "axios";

const TIMEOUT_DURATION = 10_000;

export const httpClient = axios.create({
  baseURL: process.env.API_BASE_URL,
  timeout: TIMEOUT_DURATION,
});
