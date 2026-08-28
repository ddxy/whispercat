import { writable } from 'svelte/store';

export type ToastType = 'info' | 'success' | 'error';
export interface Toast {
  id: number;
  type: ToastType;
  msg: string;
}

export const toasts = writable<Toast[]>([]);

let nextId = 0;

export function toast(type: ToastType, msg: string, ms = 4500): void {
  const t: Toast = { id: ++nextId, type, msg };
  toasts.update((list) => [...list, t]);
  setTimeout(() => {
    toasts.update((list) => list.filter((x) => x.id !== t.id));
  }, ms);
}
