import {
  Calendar,
  FileText,
  Globe,
  Headphones,
  Languages,
  Lock,
  Plane,
  Scale,
  ScrollText,
  Shield,
  ShieldCheck,
} from "lucide-react";
import type { ReactNode } from "react";

export function formalityIcon(category: string, className = "h-6 w-6 text-primary"): ReactNode {
  if (category === "VISA") {
    return <Plane className={className} aria-hidden />;
  }
  if (category === "EVISA") {
    return <Globe className={className} aria-hidden />;
  }
  if (category === "LEGALIZATION") {
    return <Scale className={className} aria-hidden />;
  }
  if (category === "APOSTILLE") {
    return <ScrollText className={className} aria-hidden />;
  }
  if (category === "TRANSLATION") {
    return <Languages className={className} aria-hidden />;
  }
  if (category === "INSURANCE") {
    return <Shield className={className} aria-hidden />;
  }
  if (category === "APPOINTMENT") {
    return <Calendar className={className} aria-hidden />;
  }
  return <FileText className={className} aria-hidden />;
}

export function reassuranceIcon(kind: "lock" | "shield" | "languages") {
  const className = "h-5 w-5 text-accent";
  if (kind === "lock") {
    return <Lock className={className} aria-hidden />;
  }
  if (kind === "shield") {
    return <ShieldCheck className={className} aria-hidden />;
  }
  return <Headphones className={className} aria-hidden />;
}

export function TrustShield({ className = "h-4 w-4" }: { className?: string }) {
  return <Shield className={className} aria-hidden />;
}
