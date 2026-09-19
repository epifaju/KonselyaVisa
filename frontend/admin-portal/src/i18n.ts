import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import en from "./locales/en/common.json";
import fr from "./locales/fr/common.json";
import pt from "./locales/pt/common.json";

void i18n.use(initReactI18next).init({
  resources: {
    fr: { common: fr },
    pt: { common: pt },
    en: { common: en },
  },
  lng: "fr",
  fallbackLng: "fr",
  ns: ["common"],
  defaultNS: "common",
  interpolation: { escapeValue: false },
});

export default i18n;
