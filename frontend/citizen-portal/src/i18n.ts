import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import { mergeLocaleParts } from "@/i18n/mergeLocaleParts";
import enCases from "./locales/en/cases.json";
import enCommon from "./locales/en/common.json";
import enError from "./locales/en/error.json";
import enJourney from "./locales/en/journey.json";
import enWizard from "./locales/en/wizard.json";
import frCases from "./locales/fr/cases.json";
import frCommon from "./locales/fr/common.json";
import frError from "./locales/fr/error.json";
import frJourney from "./locales/fr/journey.json";
import frWizard from "./locales/fr/wizard.json";
import ptCases from "./locales/pt/cases.json";
import ptCommon from "./locales/pt/common.json";
import ptError from "./locales/pt/error.json";
import ptJourney from "./locales/pt/journey.json";
import ptWizard from "./locales/pt/wizard.json";

const fr = mergeLocaleParts(frCommon, frWizard, frCases, frJourney, frError);
const en = mergeLocaleParts(enCommon, enWizard, enCases, enJourney, enError);
const pt = mergeLocaleParts(ptCommon, ptWizard, ptCases, ptJourney, ptError);

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
