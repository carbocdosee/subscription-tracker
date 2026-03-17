import 'dotenv/config';
import axios from 'axios';

const BASE_URL = process.env.BASE_URL ?? 'http://localhost:80';

export const api = axios.create({
    baseURL: BASE_URL,
    validateStatus: () => true, // never throw on 4xx/5xx — assertions handle status codes
});
