import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import { mergeLocaleParts } from "@/i18n/mergeLocaleParts";
import enCase from "./locales/en/case.json";
import enCatalog from "./locales/en/catalog.json";
import enCommon from "./locales/en/common.json";
import enOrgSettings from "./locales/en/orgSettings.json";
import enQueue from "./locales/en/queue.json";
import enSlots from "./locales/en/slots.json";
import enSupervisor from "./locales/en/supervisor.json";
import frCase from "./locales/fr/case.json";
import frCatalog from "./locales/fr/catalog.json";
import frCommon from "./locales/fr/common.json";
import frOrgSettings from "./locales/fr/orgSettings.json";
import frQueue from "./locales/fr/queue.json";
import frSlots from "./locales/fr/slots.json";
import frSupervisor from "./locales/fr/supervisor.json";
import ptCase from "./locales/pt/case.json";
import ptCatalog from "./locales/pt/catalog.json";
import ptCommon from "./locales/pt/common.json";
import ptOrgSettings from "./locales/pt/orgSettings.json";
import ptQueue from "./locales/pt/queue.json";
import ptSlots from "./locales/pt/slots.json";
import ptSupervisor from "./locales/pt/supervisor.json";

const fr = mergeLocaleParts(frCommon, frQueue, frCase, frCatalog, frSlots, frSupervisor, frOrgSettings);
const en = mergeLocaleParts(enCommon, enQueue, enCase, enCatalog, enSlots, enSupervisor, enOrgSettings);
const pt = mergeLocaleParts(ptCommon, ptQueue, ptCase, ptCatalog, ptSlots, ptSupervisor, ptOrgSettings);

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

/** Merged locale parts stay under the `common` namespace so existing `t()` keys keep working. */
export default i18n;



