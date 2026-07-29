export interface Address {
  id: number;
  label: string;
  recipientName: string;
  postalCode: string;
  street: string;
  number: string;
  complement?: string;
  neighborhood: string;
  city: string;
  state: string;
  phone?: string;
  defaultAddress: boolean;
}

export type AddressRequest = Omit<Address, 'id'>;
