import { EligibilityAssistant } from "@/screens/EligibilityAssistant";

type Props = {
  token: string;
  defaultName?: string;
  defaultEmail?: string;
  onCreated: (id: string) => void;
  onCancel: () => void;
};

export function NewCase(props: Props) {
  return <EligibilityAssistant {...props} />;
}
