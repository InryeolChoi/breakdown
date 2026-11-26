export function scoreDelivery(aimError: number, powerError: number) {
  const powerDeviation = Math.abs(powerError);
  if (aimError > 1 || powerDeviation > .4) return 0;
  return Math.round(100 - 25 * aimError - 65 * powerDeviation);
}
