export interface User {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  cpf?: string;
  active: boolean;
  roles: string[];
  createdAt?: Date;
  updatedAt?: Date;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  cpf: string;
}

export interface AuthResponse {
  token: string;
  type: 'Bearer';
  userId: number;
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
}
