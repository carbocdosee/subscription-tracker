import { Injectable, computed, signal } from "@angular/core";
import { EN } from "../../i18n/en";
import { RU } from "../../i18n/ru";
import { Translations } from "../../i18n/translations";

export type Language = "en" | "ru";

export interface LangOption {
  code: Language;
  flag: string;
  name: string;
}

const STORAGE_KEY = "app_language";

function readSavedLang(): Language {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved === "en" || saved === "ru") return saved;
  } catch {
    // localStorage not available
  }
  return "en";
}

@Injectable({ providedIn: "root" })
export class I18nService {
  readonly lang = signal<Language>(readSavedLang());

  readonly t = computed<Translations>(() => (this.lang() === "ru" ? RU : EN));

  readonly langOptions: LangOption[] = [
    { code: "en", flag: "🇺🇸", name: "English" },
    { code: "ru", flag: "🇷🇺", name: "Русский" }
  ];

  setLanguage(lang: Language): void {
    this.lang.set(lang);
    try {
      localStorage.setItem(STORAGE_KEY, lang);
    } catch {
      // ignore
    }
  }

  currentFlag(): string {
    return this.lang() === "ru" ? "🇷🇺" : "🇺🇸";
  }

  /** Russian pluralization helper. Returns `count + " " + correct_form`. */
  pluralRu(count: number, one: string, few: string, many: string): string {
    const mod10 = count % 10;
    const mod100 = count % 100;
    if (mod10 === 1 && mod100 !== 11) return `${count} ${one}`;
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return `${count} ${few}`;
    return `${count} ${many}`;
  }

  /** Format a template string: replaces {0}, {1} … with provided args. */
  fmt(template: string, ...args: (string | number)[]): string {
    return template.replace(/\{(\d+)\}/g, (_, i) => String(args[Number(i)] ?? ""));
  }
}
