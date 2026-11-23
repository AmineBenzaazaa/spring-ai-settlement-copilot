import { describeCall, describeResult, plural } from './describe';

describe('describe', () => {
  it('pluralizes in English and French', () => {
    expect(plural('en', 1, 'row', 'rows')).toBe('1 row');
    expect(plural('en', 0, 'row', 'rows')).toBe('0 rows');
    expect(plural('fr', 0, 'ligne', 'lignes')).toBe('0 ligne');
    expect(plural('fr', 2, 'ligne', 'lignes')).toBe('2 lignes');
  });

  it('turns tool calls into plain sentences', () => {
    expect(describeCall('en', 'listDisputes', '{"status":"OPEN","minAmount":400,"courierId":null}')).toBe('Looked up open disputes over 400.00 MAD');
    expect(describeCall('fr', 'listDisputes', '{"status":"OPEN","minAmount":400}')).toBe('Consulté les litiges ouverts de plus de 400,00 MAD');
    expect(describeCall('en', 'searchOrders', '{"status":"DELIVERED","city":"Casablanca","minAmount":300}')).toBe('Searched your delivered orders in Casablanca over 300.00 MAD');
    expect(describeCall('en', 'getCourierBalances', '{}')).toBe('Looked up what each courier owes you');
    expect(describeCall('en', 'somethingNew', 'not json')).toBe('somethingNew');
  });

  it('summarizes results', () => {
    expect(describeResult('en', 'OK', 1)).toBe('1 found');
    expect(describeResult('en', 'OK', 0)).toBe('nothing found');
    expect(describeResult('fr', 'OK', 3)).toBe('3 trouvés');
    expect(describeResult('en', 'NOT_FOUND', 0)).toBe('not found');
    expect(describeResult('en', 'UNAVAILABLE', 0)).toBe('failed');
  });
});
