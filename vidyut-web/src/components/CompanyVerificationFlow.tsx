import { useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { ArrowLeft, ArrowRight, Building2, Check, FileCheck2, Landmark, LockKeyhole, ShieldCheck, UserRound, X } from 'lucide-react';
import { apiRequest } from '../services/api';
import './CompanyVerificationFlow.css';

interface CompanySeed { companyName?: string; registrationNumber?: string; contactName?: string; supportEmail?: string; supportPhone?: string; businessAddress?: string; website?: string; gstNumber?: string }
interface Props { token: string; company: CompanySeed | null; onClose: () => void; onSubmitted: () => void }
type Form = Record<string, string>;
const steps = [
  { icon: Building2, title: 'Business identity', note: 'Legal registration and tax identity' },
  { icon: UserRound, title: 'Representative', note: 'Authorized company contact' },
  { icon: Landmark, title: 'Bank verification', note: 'Business settlement account' },
  { icon: FileCheck2, title: 'Charger compliance', note: 'Catalogue and safety evidence' },
];

export function CompanyVerificationFlow({ token, company, onClose, onSubmitted }: Props) {
  const [step, setStep] = useState(0);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [form, setForm] = useState<Form>({
    legalName: company?.companyName || '', cinLlpin: company?.registrationNumber || '', gstin: company?.gstNumber || '', pan: '',
    udyamNumber: '', registeredAddress: company?.businessAddress || '', website: company?.website || '',
    incorporationDocumentUrl: '', gstCertificateUrl: '', representativeName: company?.contactName || '',
    representativeWorkEmail: company?.supportEmail || '', representativePhone: company?.supportPhone || '',
    representativeDesignation: '', authorizationProofUrl: '', bankAccountHolder: company?.companyName || '', bankName: '',
    bankAccountNumber: '', ifscCode: '', cancelledChequeUrl: '', chargerCatalogueUrl: '', complianceDocumentUrl: '',
  });
  const update = (key: string, value: string) => setForm(current => ({ ...current, [key]: value }));
  const required = useMemo(() => [
    ['legalName','cinLlpin','gstin','pan','registeredAddress','incorporationDocumentUrl','gstCertificateUrl'],
    ['representativeName','representativeWorkEmail','representativePhone','representativeDesignation','authorizationProofUrl'],
    ['bankAccountHolder','bankName','bankAccountNumber','ifscCode','cancelledChequeUrl'],
    ['chargerCatalogueUrl','complianceDocumentUrl'],
  ], []);
  const validate = () => {
    if (required[step].some(key => !form[key]?.trim())) return 'Complete every required field in this verification layer.';
    if (step === 0 && !/^[0-9A-Z]{15}$/.test(form.gstin.toUpperCase())) return 'GSTIN must contain 15 uppercase letters and digits.';
    if (step === 0 && !/^[A-Z]{5}[0-9]{4}[A-Z]$/.test(form.pan.toUpperCase())) return 'Enter a valid 10-character company PAN.';
    if (step === 1 && !/^\S+@\S+\.\S+$/.test(form.representativeWorkEmail)) return 'Enter the representative’s valid work email.';
    if (step === 1 && !/^\d{10}$/.test(form.representativePhone.replace(/\D/g,''))) return 'Representative phone must contain 10 digits.';
    if (step === 2 && !/^\d{9,18}$/.test(form.bankAccountNumber)) return 'Bank account number must contain 9–18 digits.';
    if (step === 2 && !/^[A-Z]{4}0[A-Z0-9]{6}$/.test(form.ifscCode.toUpperCase())) return 'Enter a valid 11-character IFSC code.';
    return '';
  };
  const next = () => { const issue=validate(); if(issue){setError(issue);return;} setError('');setStep(current=>Math.min(3,current+1)); };
  const submit = async (event: FormEvent) => {
    event.preventDefault(); const issue=validate(); if(issue){setError(issue);return;}
    setSaving(true);setError('');
    try {
      await apiRequest('/company/verification',{method:'POST',headers:{Authorization:`Bearer ${token}`},body:JSON.stringify({
        ...form, gstin:form.gstin.toUpperCase(), pan:form.pan.toUpperCase(), ifscCode:form.ifscCode.toUpperCase(),
        representativePhone:form.representativePhone.replace(/\D/g,''),
      })});
      onSubmitted();
    } catch (submitError) { setError(submitError instanceof Error?submitError.message:'Unable to submit verification.'); }
    finally { setSaving(false); }
  };
  return <div className="company-verify-backdrop" onMouseDown={onClose}><section className="company-verify-flow" role="dialog" aria-modal="true" aria-labelledby="company-verify-title" onMouseDown={event=>event.stopPropagation()}><aside><div className="company-verify-brand"><ShieldCheck/><span><strong>Vidyut Verification</strong><small>Protected company onboarding</small></span></div><div className="company-verify-steps">{steps.map((item,index)=><button type="button" key={item.title} className={`${index===step?'active':''} ${index<step?'done':''}`} onClick={()=>index<step&&setStep(index)}><i>{index<step?<Check/>:<item.icon/>}</i><span><strong>{item.title}</strong><small>{item.note}</small></span></button>)}</div><div className="company-verify-trust"><LockKeyhole/><div><strong>Private by design</strong><p>PAN is hashed. Bank account numbers are discarded after last-four extraction. Host contact stays hidden until mutual acceptance.</p></div></div></aside><main><header><div><span>STEP {step+1} OF 4</span><h2 id="company-verify-title">{steps[step].title}</h2><p>{step===0?'Confirm the legal business behind this Vidyut workspace.':step===1?'Identify the person authorized to act for the company.':step===2?'Match a settlement account to the registered business.':'Prove the chargers are compliant before public listing.'}</p></div><button type="button" onClick={onClose} aria-label="Close verification"><X/></button></header><form onSubmit={submit}><div className="company-verify-form-scroll">{error&&<div className="company-verify-error">{error}</div>}{step===0&&<div className="company-verify-fields"><Field label="Legal company name" value={form.legalName} onChange={v=>update('legalName',v)}/><Field label="CIN / LLPIN" value={form.cinLlpin} onChange={v=>update('cinLlpin',v.toUpperCase())}/><Field label="GSTIN" value={form.gstin} maxLength={15} onChange={v=>update('gstin',v.toUpperCase())}/><Field label="Company PAN" value={form.pan} maxLength={10} secret onChange={v=>update('pan',v.toUpperCase())}/><Field label="Udyam number (optional)" value={form.udyamNumber} optional onChange={v=>update('udyamNumber',v)}/><Field label="Company website (optional)" value={form.website} optional onChange={v=>update('website',v)}/><Field label="Registered business address" value={form.registeredAddress} wide onChange={v=>update('registeredAddress',v)}/><Field label="Incorporation document URL" value={form.incorporationDocumentUrl} wide onChange={v=>update('incorporationDocumentUrl',v)}/><Field label="GST certificate URL" value={form.gstCertificateUrl} wide onChange={v=>update('gstCertificateUrl',v)}/></div>}{step===1&&<div className="company-verify-fields"><Field label="Representative full name" value={form.representativeName} onChange={v=>update('representativeName',v)}/><Field label="Designation" value={form.representativeDesignation} onChange={v=>update('representativeDesignation',v)}/><Field label="Work email" value={form.representativeWorkEmail} type="email" onChange={v=>update('representativeWorkEmail',v)}/><Field label="10-digit mobile phone" value={form.representativePhone} maxLength={10} onChange={v=>update('representativePhone',v.replace(/\D/g,''))}/><Field label="Board letter / authorization proof URL" value={form.authorizationProofUrl} wide onChange={v=>update('authorizationProofUrl',v)}/><Info icon={UserRound} title="Authorized contact" text="This person may answer review questions and enter commercial workflows after Vidyut approval."/></div>}{step===2&&<div className="company-verify-fields"><Field label="Account holder (business name)" value={form.bankAccountHolder} onChange={v=>update('bankAccountHolder',v)}/><Field label="Bank name" value={form.bankName} onChange={v=>update('bankName',v)}/><Field label="Account number" value={form.bankAccountNumber} secret onChange={v=>update('bankAccountNumber',v.replace(/\D/g,''))}/><Field label="IFSC code" value={form.ifscCode} maxLength={11} onChange={v=>update('ifscCode',v.toUpperCase())}/><Field label="Cancelled cheque / bank letter URL" value={form.cancelledChequeUrl} wide onChange={v=>update('cancelledChequeUrl',v)}/><Info icon={Landmark} title="Settlement protection" text="The full account number is never returned by the API and is not persisted in plaintext."/></div>}{step===3&&<div className="company-verify-fields"><Field label="Charger catalogue URL" value={form.chargerCatalogueUrl} wide onChange={v=>update('chargerCatalogueUrl',v)}/><Field label="Safety / compliance documents URL" value={form.complianceDocumentUrl} wide onChange={v=>update('complianceDocumentUrl',v)}/><div className="company-verify-summary"><FileCheck2/><div><strong>What happens next</strong><p>A Verification Admin reviews all four layers independently. Until approval, company contacts remain private, products stay unpublished, and proposals cannot be sent.</p><span><Check/> Business identity supplied</span><span><Check/> Representative authorization supplied</span><span><Check/> Bank match supplied</span><span><Check/> Charger evidence supplied</span></div></div></div>}</div><footer><button type="button" className="company-verify-secondary" onClick={()=>step===0?onClose():setStep(current=>current-1)}><ArrowLeft/> {step===0?'Finish later':'Back'}</button>{step<3?<button type="button" className="company-verify-primary" onClick={next}>Continue <ArrowRight/></button>:<button className="company-verify-primary" disabled={saving}>{saving?'Submitting securely…':'Submit for Vidyut review'} <ShieldCheck/></button>}</footer></form></main></section></div>;
}

function Field({label,value,onChange,wide=false,optional=false,secret=false,type='text',maxLength}:{label:string;value:string;onChange:(value:string)=>void;wide?:boolean;optional?:boolean;secret?:boolean;type?:string;maxLength?:number}) { return <label className={wide?'wide':''}><span>{label}{!optional&&<b>*</b>}</span><input type={secret?'password':type} value={value} onChange={e=>onChange(e.target.value)} maxLength={maxLength} required={!optional} autoComplete="off"/></label> }
function Info({icon:Icon,title,text}:{icon:typeof UserRound;title:string;text:string}) { return <div className="company-verify-info"><Icon/><div><strong>{title}</strong><p>{text}</p></div></div> }
